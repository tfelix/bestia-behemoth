package net.bestia.zoneserver.guild

import mu.KotlinLogging
import net.bestia.model.bestia.PlayerBestiaRepository
import net.bestia.model.findOne
import net.bestia.model.findOneOrThrow
import net.bestia.model.guild.*
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger { }

/**
 * Service to manipulate the players guild.
 *
 * @author Thomas Felix
 */
@Service
class GuildService(
    private val guildRepository: GuildRepository,
    private val guildMemberRepository: GuildMemberRepository,
    private val playerBestiaRepository: PlayerBestiaRepository
) {

  fun addPlayerToGuild(playerBestiaId: Long, guildId: Int) {
    val pb = playerBestiaRepository.findOneOrThrow(playerBestiaId)
    val guild = guildRepository.findOneOrThrow(guildId)
    if (guild.addGuildMember(pb)) {
      guildRepository.save(guild)
    }
  }

  /**
   * The maximum number of guild members depends upon the guild skill
   * SG_MAX_MEMBERS extension.
   *
   * @param guild
   * @return The calculated maximum number of guild members.
   */
  fun getMaxGuildMembers(guild: Guild?): Int {
    return if (guild == null) {
      0
    } else BASE_MAX_GUILD_MEMBERS
  }

  fun hasGuild(playerBestiaId: Long): Boolean {
    return guildMemberRepository.findByPlayerBestiaId(playerBestiaId) != null
  }

  fun getGuildOfPlayer(playerBestiaId: Long): Guild? {
    return guildMemberRepository.findByPlayerBestiaId(playerBestiaId)?.guild
  }

  fun addExpTaxToGuild(playerBestiaId: Long, earnedTotalExp: Int): Int {
    return guildMemberRepository.findByPlayerBestiaId(playerBestiaId)?.let { member ->
      val rank = member.rank
      val taxExp = Math.ceil((rank.taxRate * earnedTotalExp).toDouble()).toInt()
      val guild = member.guild
      guild.addExp(taxExp)
      guildRepository.save(guild)

      LOG.debug(String.format("Guild %d earned %d tax from pbid: %d", guild.id, taxExp, playerBestiaId))

      taxExp
    } ?: 0
  }

  fun getNeededNextLevelExp(guild: Guild): Int {
    return guild.level * 1000
  }

  fun getNeededNextLevelExp(guildId: Int): Int {
    return guildRepository.findOne(guildId)?.let { this.getNeededNextLevelExp(it) } ?: 0
  }

  fun getSkillpointsToSpend(guildId: Int): Int {
    return 0
  }

  /**
   * Checks if the given account id has a bestia in the guild.
   *
   * @param accountId
   * @param guildId
   * @return TRUE if the account id has at least one bestia inside this guild.
   */
  fun isInGuild(accountId: Long, guildId: Int): Boolean {
    return guildRepository.findOne(guildId)?.getPlayerBestiaIds()?.contains(accountId) ?: false
  }

  companion object {
    private const val BASE_MAX_GUILD_MEMBERS = 24
  }
}
