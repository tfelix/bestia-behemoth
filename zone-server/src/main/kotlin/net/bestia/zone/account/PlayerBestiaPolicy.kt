package net.bestia.zone.account

import org.springframework.stereotype.Component
import net.bestia.zone.account.persistence.Master

/**
 * A master has at most as many bestia out in the world as its account has active slots, and at most as many in
 * storage as it has storage slots.
 */
@Component
class PlayerBestiaPolicy(
  private val availableBestiaSlotService: AvailableBestiaSlotService
) {

  /** A new bestia fills an active slot first and overflows into storage. */
  fun placeFor(master: Master): BestiaPlace {
    if (hasFreeActiveSlot(master)) {
      return BestiaPlace.ACTIVE
    }

    val storageSlots = availableBestiaSlotService.getStorageSlotCount(master.account.id)
    if (master.bestias.storedBestias.size < storageSlots) {
      return BestiaPlace.STORAGE
    }

    throw OwnedBestiaPolicyViolationException("Master ${master.id} has no free active or storage slot")
  }

  fun hasFreeActiveSlot(master: Master): Boolean {
    val activeSlots = availableBestiaSlotService.getTotalSlotCount(master.account.id)

    return master.bestias.activeBestias.size < activeSlots
  }
}
