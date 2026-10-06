package net.bestia.zone.account

import net.bestia.zone.bestia.Bestia
import org.springframework.stereotype.Component
import net.bestia.zone.account.persistence.Master

/**
 * A master owns at most as many bestia as its account has slots.
 */
@Component
class PlayerBestiaPolicy(
  private val availableBestiaSlotService: AvailableBestiaSlotService
) {

  fun checkPolicy(master: Master, addedBestia: Bestia) {
    val availableSlots = availableBestiaSlotService.getTotalSlotCount(master.account.id)

    if (master.bestias.ownedBestias.size + 1 > availableSlots) {
      throw OwnedBestiaPolicyViolationException("You can not have more than $availableSlots Bestia")
    }
  }
}
