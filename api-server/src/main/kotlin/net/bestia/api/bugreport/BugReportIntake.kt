package net.bestia.api.bugreport

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/** Keeps a report until the publisher takes it, unless its account is over its limits or shadow-banned. */
@Service
class BugReportIntake(
  private val properties: BugReportProperties,
  private val reports: BugReportRepository,
  private val files: BugReportFileRepository,
  private val bans: BugReportBanRepository,
) {

  data class Submission(
    val accountId: Long,
    val masterId: Long?,
    val kind: BugReport.Kind,
    val signature: String?,
    val clientVersion: String?,
    val text: String,
    val files: List<BugReportAttachment.Prepared>,
  )

  enum class Outcome {
    /** Also the answer to a shadow-banned account, so it cannot tell. */
    ACCEPTED,
    RATE_LIMITED,
  }

  @Transactional
  fun submit(submission: Submission): Outcome {
    if (isOverLimit(submission.accountId)) {
      return Outcome.RATE_LIMITED
    }

    if (bans.existsById(submission.accountId)) {
      // Kept without its content, so a banned account still counts against its limits.
      reports.save(reportOf(submission, text = null, status = BugReport.Status.SHADOWED))
      return Outcome.ACCEPTED
    }

    val report = reports.save(reportOf(submission, submission.text, BugReport.Status.PENDING))
    files.saveAll(submission.files.map { BugReportFile(report.id, it.name, it.content) })

    return Outcome.ACCEPTED
  }

  private fun isOverLimit(accountId: Long): Boolean {
    val now = LocalDateTime.now()
    val overHourly = properties.perHour?.let { reports.countByAccountIdAndCreatedAtAfter(accountId, now.minusHours(1)) >= it }
    val overDaily = properties.perDay?.let { reports.countByAccountIdAndCreatedAtAfter(accountId, now.minusDays(1)) >= it }

    return overHourly == true || overDaily == true
  }

  private fun reportOf(submission: Submission, text: String?, status: BugReport.Status): BugReport {
    return BugReport(
      accountId = submission.accountId,
      masterId = submission.masterId,
      kind = submission.kind,
      signature = submission.signature,
      clientVersion = submission.clientVersion,
      text = text,
      status = status,
    )
  }
}
