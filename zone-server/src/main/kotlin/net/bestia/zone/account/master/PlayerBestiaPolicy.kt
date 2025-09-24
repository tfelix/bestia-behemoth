package net.bestia.zone.account.master

import net.bestia.zone.ZoneConfig
import net.bestia.zone.account.AvailableBestiaSlotService
import net.bestia.zone.bestia.Bestia
import org.springframework.stereotype.Component

/**
 * Regular policy which checks the policy regarding owned bestia.
 */
@Component
class PlayerBestiaPolicy(
  private val zoneConfig: ZoneConfig,
  private val availableBestiaSlotService: AvailableBestiaSlotService
) {

  fun checkPolicy(master: Master, addedBestia: Bestia) {
    val availableSlots = availableBestiaSlotService.getTotalSlotCount(master.account.id)

    if (availableSlots + 1 > zoneConfig.bestiaMaxSlotCount) {
      throw OwnedBestiaPolicyViolationException("You can not have more than ${zoneConfig.bestiaMaxSlotCount} Bestia")
    }
  }
}