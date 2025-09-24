package net.bestia.zone.account

import jakarta.persistence.*
import net.bestia.zone.account.master.Master
import net.bestia.zone.util.AccountId

@Entity
@Table(
  name = "account",
  indexes = [
    Index(name = "idx_login_account_id", columnList = "loginAccountId", unique = true),
  ]
)
class Account(
  @Column(unique = true)
  val loginAccountId: Long
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: AccountId = 0

  @OneToMany(mappedBy = "account", cascade = [CascadeType.ALL])
  val master: MutableSet<Master> = mutableSetOf()

  var additionalMasterSlots: Int = 0

  var additionalBestiaSlots: Int = 0

  companion object {
    const val DEFAULT_MASTER_SLOT_COUNT = 3
    const val DEFAULT_BESTIA_SLOT_COUNT = 4
  }
}
