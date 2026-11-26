package net.bestia.api.bugreport

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

@Entity
@Table(name = "bug_report")
class BugReport(
  @Column(name = "account_id", nullable = false)
  val accountId: Long,

  @Column(name = "master_id", nullable = true)
  val masterId: Long?,

  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  val kind: Kind,

  @Column(nullable = true, length = 64, columnDefinition = "CHAR(64)")
  val signature: String?,

  @Column(name = "client_version", nullable = true, length = 64)
  val clientVersion: String?,

  @Column(nullable = true, columnDefinition = "TEXT")
  val text: String?,

  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  var status: Status,

  @Column(name = "created_at", nullable = false)
  val createdAt: LocalDateTime = LocalDateTime.now(),
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  @Column(name = "issue_number", nullable = true)
  var issueNumber: Int? = null

  @Column(nullable = false)
  var attempts: Int = 0

  @Column(name = "next_attempt_at", nullable = true)
  var nextAttemptAt: LocalDateTime? = null

  enum class Kind {
    /** Written by the player. */
    MANUAL,

    /** Sent by the client on its own after an error. */
    CRASH,
  }

  enum class Status {
    PENDING,
    SENT,
    FAILED,

    /** From a shadow-banned account: answered as accepted, never published. */
    SHADOWED,
  }
}
