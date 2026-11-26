package net.bestia.api.bugreport

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime

/**
 * Shadow bans for bug reports. The id in an issue's summary is enough to ban its sender, so nobody has to look up the
 * account. Banning also drops the account's reports that are still waiting.
 */
@RestController
@RequestMapping("/v1/admin/bug-reports")
class BugReportAdminController(
  private val bans: BugReportBanRepository,
  private val reports: BugReportRepository,
  private val files: BugReportFileRepository,
) {

  data class Ban(val accountId: Long, val reason: String?, val createdAt: LocalDateTime)

  data class BanRequest(val reason: String? = null)

  @GetMapping("/bans")
  fun bans(): List<Ban> {
    return bans.findAll().map { Ban(it.accountId, it.reason, it.createdAt) }
  }

  @PutMapping("/bans/{accountId}")
  @Transactional
  fun ban(@PathVariable accountId: Long, @RequestBody(required = false) body: BanRequest?): Ban {
    return banAccount(accountId, body?.reason)
  }

  @PutMapping("/{reportId}/ban-sender")
  @Transactional
  fun banSender(@PathVariable reportId: Long, @RequestBody(required = false) body: BanRequest?): Ban {
    val report = reports.findById(reportId).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No report $reportId") }

    return banAccount(report.accountId, body?.reason ?: "report $reportId")
  }

  @DeleteMapping("/bans/{accountId}")
  @Transactional
  fun unban(@PathVariable accountId: Long): ResponseEntity<Void> {
    bans.deleteById(accountId)
    return ResponseEntity.noContent().build()
  }

  private fun banAccount(accountId: Long, reason: String?): Ban {
    val ban = bans.findById(accountId).orElse(null) ?: bans.save(BugReportBan(accountId, reason?.take(MAX_REASON_LENGTH)))
    dropWaitingReports(accountId)

    return Ban(ban.accountId, ban.reason, ban.createdAt)
  }

  private fun dropWaitingReports(accountId: Long) {
    reports.findAllByAccountIdAndStatus(accountId, BugReport.Status.PENDING).forEach {
      it.status = BugReport.Status.SHADOWED
      files.deleteAllOfReport(it.id)
    }
  }

  private companion object {
    const val MAX_REASON_LENGTH = 255
  }
}
