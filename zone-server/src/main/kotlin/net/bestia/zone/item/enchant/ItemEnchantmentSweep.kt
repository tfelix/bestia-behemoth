package net.bestia.zone.item.enchant

import net.bestia.zone.item.persistence.ItemInstanceEnchantmentRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** Deletes ended enchantment rows. Reads already ignore them, so this only keeps the table small. */
@Component
class ItemEnchantmentSweep(
  private val enchantments: ItemInstanceEnchantmentRepository,
  private val clock: Clock = Clock.systemUTC(),
) {

  @Scheduled(fixedDelay = 6 * 60 * 60 * 1000L)
  @Transactional
  fun deleteEnded() {
    enchantments.deleteEnded(clock.instant())
  }
}
