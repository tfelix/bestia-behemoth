package net.bestia.zone.account

import net.bestia.zone.config.ZoneConfig
import net.bestia.zone.util.AccountId
import org.springframework.stereotype.Service
import kotlin.math.min
import net.bestia.zone.account.persistence.AccountRepository
import net.bestia.zone.account.persistence.findByIdOrThrow

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