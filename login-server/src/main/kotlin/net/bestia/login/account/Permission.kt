package net.bestia.login.account

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import net.bestia.account.Authority

@Entity
@Table(name = "permission")
class Permission(
  @ManyToOne
  @JoinColumn(name = "account_id", nullable = false)
  val account: Account,

  val authority: Authority
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}