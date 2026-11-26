package net.bestia.api.bugreport

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/** An account whose bug reports are answered as if accepted, and then dropped. */
@Entity
@Table(name = "bug_report_ban")
class BugReportBan(
  @Id
  @Column(name = "account_id", nullable = false)
  val accountId: Long,

  @Column(nullable = true, length = 255)
  val reason: String?,

  @Column(name = "created_at", nullable = false)
  val createdAt: LocalDateTime = LocalDateTime.now(),
)
