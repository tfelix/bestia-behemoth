package net.bestia.login.account

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
import net.bestia.account.Role
import java.time.LocalDateTime

/**
 * The account holds identity and authorization only. How an account can authenticate is modelled by
 * the independent login method entities (see [net.bestia.login.account.loginmethod.LoginMethod]),
 * each of which links back to an account.
 */
@Entity
@Table(
  name = "account",
  indexes = [
    Index(name = "idx_account_status", columnList = "status")
  ]
)
data class Account(
  // VARCHAR, not the native ENUM the MariaDB dialect would otherwise pick: an ENUM column
  // makes adding a value a schema migration and makes the declared order load-bearing.
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  val role: Role = Role.USER,

  @Column(nullable = false)
  val createdAt: LocalDateTime = LocalDateTime.now()
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  // VARCHAR, not the native ENUM the MariaDB dialect would otherwise pick: an ENUM column
  // makes adding a value a schema migration and makes the declared order load-bearing.
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  var status: AccountStatus = AccountStatus.ACTIVE

  @Column(nullable = true)
  var bannedUntil: LocalDateTime? = null

  @Column
  var lastLogin: LocalDateTime? = null

  /**
   * Names the account, not the player. It is what the operating system's passkey picker shows next
   * to the credential, and what recovery is looked up by; the name other players see is the
   * master's, chosen later in the game and stored on the zone.
   *
   * Null for the accounts that predate the passkey flow (NFT, static dev token), which have no name
   * of their own.
   */
  @Column(nullable = true, length = 32, unique = true)
  var displayName: String? = null
}
