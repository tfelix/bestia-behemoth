package net.bestia.api.bugreport

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Duration
import java.time.LocalDateTime

/** The issue a crash signature was published as. */
@Entity
@Table(name = "bug_crash_signature")
class BugCrashSignature(
  @Id
  @Column(nullable = false, length = 64, columnDefinition = "CHAR(64)")
  val signature: String,

  @Column(name = "issue_number", nullable = false)
  val issueNumber: Int,

  @Column(name = "last_comment_at", nullable = true)
  var lastCommentAt: LocalDateTime? = null,
) {

  fun isCommentDue(now: LocalDateTime, interval: Duration): Boolean {
    return lastCommentAt?.let { !it.plus(interval).isAfter(now) } ?: true
  }
}
