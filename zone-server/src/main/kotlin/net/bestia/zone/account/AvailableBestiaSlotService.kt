package net.bestia.zone.account

import net.bestia.zone.ZoneConfig
import net.bestia.zone.util.AccountId
import org.springframework.stereotype.Service
import kotlin.math.min

@Service
class AvailableBestiaSlotService(
  private val config: ZoneConfig,
  private val accountRepository: AccountRepository,
) {

  fun getTotalSlotCount(accountId: AccountId): Int {
    val account = accountRepository.findByIdOrThrow(accountId)
    val availableSlots = account.additionalBestiaSlots + config.bestiaBaseSlotCount

    return min(availableSlots, config.bestiaMaxSlotCount)
  }
}