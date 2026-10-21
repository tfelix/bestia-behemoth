package net.bestia.zone.companion.query

import net.bestia.zone.account.persistence.Master
import net.bestia.zone.account.persistence.MasterRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

/** The one ownership rule every companion query goes through: a master is only ever read by its own account. */
@Component
class OwnedMasters(private val masters: MasterRepository) {

  /** [masterId] if [accountId] owns it, else null - which the caller reports exactly like a master that is not there. */
  fun find(accountId: Long, masterId: Long): Master? {
    return masters.findByIdOrNull(masterId)?.takeIf { it.account.id == accountId }
  }
}
