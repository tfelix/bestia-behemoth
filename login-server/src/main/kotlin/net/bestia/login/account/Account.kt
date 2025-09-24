package net.bestia.login.account

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(
  name = "account",
  indexes = [
    Index(name = "idx_account_token_id", columnList = "nftTokenId"),
    Index(name = "idx_account_status", columnList = "status")
  ]
)
data class Account(
  @Column(nullable = false)
  val nftTokenId: Long,

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

  @OneToMany(mappedBy = "account", cascade = [CascadeType.ALL])
  val permissions: MutableSet<Permission> = mutableSetOf()
}
