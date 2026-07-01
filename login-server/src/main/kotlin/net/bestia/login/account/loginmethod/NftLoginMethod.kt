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
 * Authentication via ownership of an NFT. The account is identified by the NFT token id; wallet and
 * signature are supplied per login request and verified against the chain.
 */
@Entity
@Table(
  name = "nft_login_method",
  indexes = [
    Index(name = "idx_nft_login_method_token_id", columnList = "nftTokenId", unique = true)
  ]
)
class NftLoginMethod(
  @ManyToOne
  @JoinColumn(name = "account_id", nullable = false)
  override val account: Account,

  @Column(nullable = false, unique = true)
  val nftTokenId: Long,

  @Column(nullable = false)
  override val createdAt: LocalDateTime = LocalDateTime.now()
) : LoginMethod {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  @Column
  override var lastUsedAt: LocalDateTime? = null
}
