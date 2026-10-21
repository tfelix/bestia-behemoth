package net.bestia.zone.guild

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.guild.persistence.GuildMemberRepository
import net.bestia.zone.guild.persistence.GuildRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Writes one flush of taxed EXP into a guild, levelling it up as far as it reaches. */
@Component
class GuildExpWriter(
  private val guildRepository: GuildRepository,
  private val memberRepository: GuildMemberRepository,
  private val levels: GuildLevels,
) {

  /**
   * [contributions] is EXP by master id. @return whether the guild took it - false when it was disbanded
   * meanwhile, and its EXP is dropped with it.
   */
  @Transactional
  fun apply(guildId: Long, contributions: Map<Long, Long>): Boolean {
    val guild = guildRepository.findByIdForUpdate(guildId) ?: return false
    val total = contributions.values.sum()

    val progress = levels.addExp(guild.level, guild.exp, total)
    guild.level = progress.level
    guild.exp = progress.exp

    // A member who left since paying in is simply not updated; the guild keeps the EXP.
    contributions.forEach { (masterId, amount) -> memberRepository.addContribution(guildId, masterId, amount) }

    if (progress.levelsGained > 0) {
      LOG.info { "Guild $guildId reached level ${progress.level}" }
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
