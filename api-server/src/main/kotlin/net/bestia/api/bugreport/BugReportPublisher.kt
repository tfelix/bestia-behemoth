package net.bestia.api.bugreport

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import java.time.Duration
import java.time.LocalDateTime

/**
 * Turns waiting reports into issues in the reports repo, and retries with backoff while GitHub refuses. A crash whose
 * signature already has an issue joins it instead. Only one api-server instance may have the token: two would
 * publish the same report twice.
 */
@Component
class BugReportPublisher(
  properties: BugReportProperties,
  private val reports: BugReportRepository,
  private val files: BugReportFileRepository,
  private val crashes: BugCrashSignatureRepository,
  private val github: GitHubReports,
) {

  private val config = properties.github
  private val crashCommentInterval = properties.crashCommentInterval

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
      val knownCrash = report.signature?.let { crashes.findById(it).orElse(null) }
      val issueNumber = if (knownCrash != null) {
        joinCrashIssue(report, reportFiles, knownCrash)
      } else {
        openIssue(report, reportFiles)
      }
      markSent(report, issueNumber)
    } catch (e: RestClientException) {
      retryLater(report, e)
    }
  }

  private fun openIssue(report: BugReport, reportFiles: List<BugReportFile>): Int {
    val links = commitFiles(report, reportFiles)
    val issue = BugReportIssue.of(report, reportFiles, links, report.signature?.let { occurrencesOf(report, it) })
    val number = github.openIssue(issue.title, issue.body, issue.labels)
    report.signature?.let { crashes.save(BugCrashSignature(it, number, lastCommentAt = LocalDateTime.now())) }

    return number
  }

  private fun joinCrashIssue(report: BugReport, reportFiles: List<BugReportFile>, crash: BugCrashSignature): Int {
    val now = LocalDateTime.now()
    if (crash.isCommentDue(now, crashCommentInterval)) {
      github.comment(crash.issueNumber, BugReportIssue.commentOf(report, reportFiles, commitFiles(report, reportFiles)))
      // Saved before the body edit, so a retry after a failed edit does not comment twice.
      crash.lastCommentAt = now
      crashes.save(crash)
    }

    val occurrences = occurrencesOf(report, crash.signature)
    BugReportIssue.withOccurrences(github.issueBody(crash.issueNumber), occurrences)
      ?.let { github.editIssueBody(crash.issueNumber, it) }

    return crash.issueNumber
  }

  /** Counted from the stored reports rather than kept as a counter, so a retried report is never counted twice. */
  private fun occurrencesOf(report: BugReport, signature: String): BugReportIssue.Occurrences {
    val count = reports.countBySignatureAndStatusIn(signature, COUNTED_STATUSES)

    return BugReportIssue.Occurrences(count, report.createdAt, report.clientVersion)
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
    val COUNTED_STATUSES = listOf(BugReport.Status.PENDING, BugReport.Status.SENT)
  }
}
