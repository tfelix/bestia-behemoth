package net.bestia.zone.account.master

import net.bestia.zone.account.AvailableBestiaSlotService
import net.bestia.zone.bestia.Bestia
import org.springframework.stereotype.Component

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
