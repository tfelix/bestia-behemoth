package net.bestia.zone.account

import jakarta.persistence.*
import net.bestia.zone.account.master.Master
import net.bestia.zone.util.AccountId

/**
 * A zone's local view of an account.
 *
 * The id is assigned rather than generated, and it is the login-server's account id — the number the
 * client's JWT carries and every `CMSG.playerId` is stamped with. A generated id would be a second,
 * per-zone identity for the same player that has to be translated at every boundary: a player is routed
 * to whichever zone is available, so the same account materializes independently in each one and a
 * generated sequence would hand out a different number in every zone. Everything downstream —
 * [net.bestia.zone.ecs.core.session.ConnectionInfoService], the channel registry, party membership —
 * keys off the JWT's id, so that is the id this row must have.
 *
 * Rows are created on first connect by [AccountProvisioningService], not by any registration flow here.
 */
@Entity
@Table(name = "account")
class Account(
  @Id
  val id: AccountId
) {
  @OneToMany(mappedBy = "account", cascade = [CascadeType.ALL])
  val master: MutableSet<Master> = mutableSetOf()

  var additionalMasterSlots: Int = 0

  var additionalBestiaSlots: Int = 0

  companion object {
    const val DEFAULT_MASTER_SLOT_COUNT = 3
    const val DEFAULT_BESTIA_SLOT_COUNT = 4
  }
}
