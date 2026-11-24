package net.bestia.zone.account

import net.bestia.zone.bestia.persistence.Bestia
import org.springframework.stereotype.Component
import net.bestia.zone.account.persistence.Master

/**
 * A master has at most as many bestia out in the world as its account has active slots.
 */
@Component
class PlayerBestiaPolicy(
  private val availableBestiaSlotService: AvailableBestiaSlotService
) {

  fun checkPolicy(master: Master, addedBestia: Bestia) {
    val availableSlots = availableBestiaSlotService.getTotalSlotCount(master.account.id)

    if (master.bestias.activeBestias.size + 1 > availableSlots) {
      throw OwnedBestiaPolicyViolationException("You can not have more than $availableSlots Bestia")
    }
  }
}
