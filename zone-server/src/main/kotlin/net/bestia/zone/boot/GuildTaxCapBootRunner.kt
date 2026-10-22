package net.bestia.zone.boot

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.guild.GuildConfig
import net.bestia.zone.guild.persistence.GuildRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Holds ranks saved under a higher `guild.max-tax-percent` to the current one, so no member pays more than it. */
@Component
@Order(105)
class GuildTaxCapBootRunner(
  private val guildRepository: GuildRepository,
  private val config: GuildConfig,
) : CommandLineRunner {

  @Transactional
  override fun run(vararg args: String?) {
    val lowered = guildRepository.capRankTaxes(config.maxTaxPercent)

    if (lowered > 0) {
      LOG.info { "Lowered the tax of $lowered guild ranks to ${config.maxTaxPercent}%" }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
