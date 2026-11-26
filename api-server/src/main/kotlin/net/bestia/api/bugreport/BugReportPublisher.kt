package net.bestia.api.bugreport

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import java.time.Duration
import java.time.LocalDateTime

/**
 * Turns waiting reports into issues in the reports repo, and retries with backoff while GitHub refuses. Only one
 * api-server instance may have the token: two would publish the same report twice.
 */
@Component
class BugReportPublisher(
  properties: BugReportProperties,
  private val reports: BugReportRepository,
  private val files: BugReportFileRepository,
  private val github: GitHubReports,
) {

  private val config = properties.github

  @Scheduled(fixedDelayString = "\${bug-reports.github.publish-interval:PT10S}")
  fun publishDue() {
    if (!config.isConfigured) {
      return
    }

    reports.findDue(BugReport.Status.PENDING, LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE)).forEach { publish(it) }
  }

  private fun publish(report: BugReport) {
    try {
      val reportFiles = files.findAllByReportId(report.id)
      val links = commitFiles(report, reportFiles)
      val issue = BugReportIssue.of(report, reportFiles, links)
      val number = github.openIssue(issue.title, issue.body, issue.labels)
      markSent(report, number)
    } catch (e: RestClientException) {
      retryLater(report, e)
    }
  }

  private fun commitFiles(report: BugReport, reportFiles: List<BugReportFile>): BugReportIssue.Files? {
    if (reportFiles.isEmpty()) {
      return null
    }

    val folder = folderOf(report)
    val commit = github.commit("Report ${report.id}", reportFiles.associate { "$folder/${it.name}" to it.content })

    return BugReportIssue.Files(config.webUrl, config.repo!!, commit, folder)
  }

  private fun markSent(report: BugReport, issueNumber: Int) {
    report.status = BugReport.Status.SENT
    report.issueNumber = issueNumber
    reports.save(report)
    // After the status: a crash between the two leaves files behind, never a report published twice.
    files.deleteAllOfReport(report.id)
  }

  private fun retryLater(report: BugReport, error: RestClientException) {
    report.attempts += 1
    if (report.attempts >= MAX_ATTEMPTS) {
      report.status = BugReport.Status.FAILED
      LOG.error(error) { "Bug report ${report.id} could not be published after ${report.attempts} attempts; giving up" }
    } else {
      report.nextAttemptAt = LocalDateTime.now().plus(backoffAfter(report.attempts))
      LOG.warn { "Bug report ${report.id} could not be published (attempt ${report.attempts}): ${error.message}" }
    }
    reports.save(report)
  }

  private fun backoffAfter(attempts: Int): Duration {
    val minutes = 1L shl (attempts - 1).coerceAtMost(MAX_BACKOFF_DOUBLINGS)

    return Duration.ofMinutes(minutes).coerceAtMost(MAX_BACKOFF)
  }

  private fun folderOf(report: BugReport): String {
    return "reports/%04d/%02d/%d".format(report.createdAt.year, report.createdAt.monthValue, report.id)
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
    const val BATCH_SIZE = 10
    const val MAX_ATTEMPTS = 10
    const val MAX_BACKOFF_DOUBLINGS = 16
    val MAX_BACKOFF: Duration = Duration.ofHours(6)
  }
}
