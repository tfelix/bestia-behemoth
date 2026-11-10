package net.bestia.zone.boot

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.battle.status.IndividualValues
import net.bestia.zone.bestia.Sex
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import kotlin.random.Random

/**
 * Rolls a sex and individual values for every player bestia caught before bestias had them, as if it had been
 * caught today. A bestia created since gets both at creation.
 */
@Component
@Order(110)
class PlayerBestiaGeneticsBootRunner(
  private val playerBestiaRepository: PlayerBestiaRepository,
) : CommandLineRunner {

  @Transactional
  override fun run(vararg args: String?) {
    val withoutSex = playerBestiaRepository.findAllBySexIsNull()
    if (withoutSex.isEmpty()) {
      return
    }

    withoutSex.forEach {
      it.sex = Sex.roll(Random.Default)
      it.individualValues = IndividualValues.rollWild(Random.Default)
    }

    LOG.info { "Rolled sex and individual values for ${withoutSex.size} player bestia(s)" }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
