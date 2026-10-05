package net.bestia.login.admin

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/** One ban or kick a GM made, kept so every action can be traced back to who did it and why. */
@Entity
@Table(
  name = "gm_action",
  indexes = [
    Index(name = "idx_gm_action_target", columnList = "target_account_id")
  ]
)
class GmAction(
  @Column(nullable = false)
  val actorAccountId: Long,

  @Column(nullable = false)
  val targetAccountId: Long,

  // VARCHAR, not the native ENUM the MariaDB dialect would otherwise pick: an ENUM column
  // makes adding a value a schema migration and makes the declared order load-bearing.
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  val action: GmActionType,

  @Column(nullable = false, length = MAX_NOTE_LENGTH)
  val note: String,

  @Column(nullable = true)
  val bannedUntil: LocalDateTime? = null,

  @Column(nullable = false)
  val createdAt: LocalDateTime = LocalDateTime.now()
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  companion object {
    const val MAX_NOTE_LENGTH = 255
  }
}
