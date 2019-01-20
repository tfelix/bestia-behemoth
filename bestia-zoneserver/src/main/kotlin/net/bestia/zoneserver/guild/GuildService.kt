package net.bestia.zoneserver.guild

import mu.KotlinLogging
import net.bestia.model.dao.*
import net.bestia.model.guild.Guild
import net.bestia.model.guild.GuildMember
import net.bestia.model.domain.PlayerBestia
import net.bestia.model.guild.GuildRepository
import net.bestia.model.guild.GuildMemberRepository
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger { }

/**
 * Service to manipulate the players guild.
 *
 * @author Thomas Felix
 */
@Service
class GuildService(
    private val guildDao: GuildRepository,
    private val guildMemberDao: GuildMemberRepository,
    private val playerBestiaDao: PlayerBestiaDAO
) {

  fun addPlayerToGuild(playerBestiaId: Long, guildId: Int) {
    val pb = playerBestiaDao.findOneOrThrow(playerBestiaId) ?: return

    val guild = guildDao.findOneOrThrow(guildId)
    if (guild.members.size >= getMaxGuildMembers(guild)) {
      guildMemberDao.findOne(guildId)?.also {
        val gm = GuildMember(guild, pb)
        guildMemberDao.save(gm)
      }
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
    return guildMemberDao.findByPlayerBestiaId(playerBestiaId) != null
  }

  fun getGuildOfPlayer(playerBestiaId: Long): Guild? {
    return guildMemberDao.findByPlayerBestiaId(playerBestiaId)?.guild
  }

  fun addExpTaxToGuild(playerBestiaId: Long, earnedTotalExp: Int): Int {
    return guildMemberDao.findByPlayerBestiaId(playerBestiaId)?.let { member ->
      val rank = member.rank
      val taxExp = Math.ceil((rank!!.taxRate * earnedTotalExp).toDouble()).toInt()
      val guild = member.guild
      guild.addExp(taxExp)
      guildDao.save(guild)

      LOG.debug(String.format("Guild %d earned %d tax from pbid: %d", guild.id, taxExp, playerBestiaId))

      taxExp
    } ?: 0
  }

  fun getNeededNextLevelExp(guild: Guild): Int {
    return guild.level * 1000
  }

  fun getNeededNextLevelExp(guildId: Int): Int {
    return guildDao.findOne(guildId)?.let { this.getNeededNextLevelExp(it) } ?: 0
  }

  fun getGuildMembersFromPlayer(playerBestiaId: Long): Set<PlayerBestia> {
    return getGuildOfPlayer(playerBestiaId)?.members
        ?.asSequence()
        ?.map { it.member }
        ?.filter { x -> x.id != playerBestiaId }
        ?.toSet()
        ?: emptySet()
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
    return guildDao.findOne(guildId)?.let { guild ->
      guild.members.stream().anyMatch { m -> m.member.owner.id == accountId }
    } ?: false
  }

  companion object {
    private const val BASE_MAX_GUILD_MEMBERS = 24
  }
}
