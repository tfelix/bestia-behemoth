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
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  val role: Role = Role.USER,

  @Column(nullable = false)
  val createdAt: LocalDateTime = LocalDateTime.now()
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  @Column(nullable = false, length = 20)
  var status: AccountStatus = AccountStatus.ACTIVE

  @Column(nullable = true)
  var bannedUntil: LocalDateTime? = null

  @Column
  var lastLogin: LocalDateTime? = null
}
