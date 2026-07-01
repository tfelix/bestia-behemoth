package net.bestia.login.account.loginmethod

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import net.bestia.login.account.Account
import java.time.LocalDateTime

/**
 * Simple username + static token authentication, intended for development so the game can be used
 * without any blockchain/NFT setup.
 */
@Entity
@Table(
  name = "static_token_login_method",
  indexes = [
    Index(name = "idx_static_login_method_username", columnList = "username", unique = true)
  ]
)
class StaticTokenLoginMethod(
  @ManyToOne
  @JoinColumn(name = "account_id", nullable = false)
  override val account: Account,

  @Column(nullable = false, unique = true, length = 64)
  val username: String,

  @Column(nullable = false, length = 128)
  val staticToken: String,

  @Column(nullable = false)
  override val createdAt: LocalDateTime = LocalDateTime.now()
) : LoginMethod {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  @Column
  override var lastUsedAt: LocalDateTime? = null
}
