package net.bestia.zoneserver.guild

import net.bestia.model.dao.GuildDAO
import net.bestia.model.dao.GuildMemberDAO
import net.bestia.model.dao.PlayerBestiaDAO
import net.bestia.model.domain.Guild
import net.bestia.model.domain.GuildMember
import net.bestia.model.domain.PlayerBestia
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

/**
 * Service to manipulate the players guild.
 *
 * @author Thomas Felix
 */
@Service
class GuildService(
        private val guildDao: GuildDAO,
        private val guildMemberDao: GuildMemberDAO,
        private val playerBestiaDao: PlayerBestiaDAO
) {

  fun addPlayerToGuild(playerBestiaId: Long, guildId: Int) {
    val pb = playerBestiaDao.findOne(playerBestiaId) ?: return

    guildDao.findOne(guildId).ifPresent { guild ->
      if (guild.members.size >= getMaxGuildMembers(guild)) {
        guildMemberDao.findOne(guildId)?.also {
          val gm = GuildMember(guild, pb)
          guildMemberDao.save(gm)
        }
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

  fun getGuildOfPlayer(playerBestiaId: Long): Optional<Guild> {
    return guildMemberDao.findByPlayerBestiaId(playerBestiaId).map { it.guild }
  }

  fun addExpTaxToGuild(playerBestiaId: Long, earnedTotalExp: Int): Int {
    return guildMemberDao.findByPlayerBestiaId(playerBestiaId).map { member ->
      val rank = member.rank
      if (rank == null) {
        0
      }

      val taxExp = Math.ceil((rank!!.taxRate * earnedTotalExp).toDouble()).toInt()
      val guild = member.guild
      guild.addExp(taxExp)
      guildDao.save(guild)

      LOG.debug(String.format("Guild %d earned %d tax from pbid: %d", guild.id, taxExp, playerBestiaId))

      taxExp
    }.orElse(0)
  }

  fun getNeededNextLevelExp(guild: Guild): Int {
    return guild.level * 1000
  }

  fun getNeededNextLevelExp(guildId: Int): Int {
    return guildDao.findOne(guildId)
            .map { this.getNeededNextLevelExp(it) }
            .orElse(0)
  }

  fun getGuildMembersFromPlayer(playerBestiaId: Long): Set<PlayerBestia> {
    return getGuildOfPlayer(playerBestiaId).map { guild ->
      guild.members.map { it.member }
              .filter { x -> x.id != playerBestiaId }
              .toSet()
    }.orElse(emptySet())
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
    val optGuild = guildDao.findOne(guildId)

    return optGuild.map { guild ->
      guild.members.stream().anyMatch { m -> m.member.owner.id == accountId }
    }.orElse(false)
  }

  companion object {
    private const val BASE_MAX_GUILD_MEMBERS = 24
    private val LOG = LoggerFactory.getLogger(GuildService::class.java)
  }
}
