package net.bestia.zone.stronghold

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.World
import net.bestia.zone.guild.GuildException
import net.bestia.zone.guild.GuildNoticeSMSG
import net.bestia.zone.guild.GuildNoticeSMSG.Notice
import net.bestia.zone.guild.GuildService
import net.bestia.zone.guild.persistence.GuildLogKind
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.persistence.AsyncJobExecutor
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Tells a guild what happens to its Strongholds: a notice to every member being played, and an entry in the guild
 * log for what the guild should still read about later. Tick thread only.
 */
@Service
class StrongholdNews(
  private val seeds: EmpyriteSeedRegistry,
  private val messages: OutMessageProcessor,
  private val guilds: GuildService,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val clock: Clock = Clock.systemUTC(),
) {

  /** When each Stronghold last said it was under attack. */
  private val attackToldAt = HashMap<Int, Instant>()

  fun planted(world: World, guildId: Long, rank: Int, planterName: String) {
    tell(world, guildId, Notice.EMPYRITE_PLANTED, rank)
    log(guildId, GuildLogKind.EMPYRITE_PLANTED, planterName, rank)
  }

  fun stands(world: World, guildId: Long, rank: Int) {
    tell(world, guildId, Notice.STRONGHOLD_STANDS, rank)
    log(guildId, GuildLogKind.STRONGHOLD_STANDS, "", rank)
  }

  /** A rival struck the Empyrite. Said once, and again only after a quiet spell, so a siege is not a flood. */
  fun underAttack(world: World, guildId: Long, rank: Int) {
    val now = clock.instant()
    val last = attackToldAt[rank]
    if (last != null && Duration.between(last, now) < ATTACK_QUIET) return

    attackToldAt[rank] = now
    tell(world, guildId, Notice.EMPYRITE_UNDER_ATTACK, rank)
  }

  fun captured(world: World, newGuildId: Long, oldGuildId: Long, rank: Int) {
    tell(world, newGuildId, Notice.STRONGHOLD_CAPTURED, rank)
    log(newGuildId, GuildLogKind.STRONGHOLD_CAPTURED, "", rank)
    lost(world, oldGuildId, rank)
  }

  /** Captured, broken, or its site left too long. */
  fun lost(world: World, guildId: Long, rank: Int) {
    tell(world, guildId, Notice.STRONGHOLD_LOST, rank)
    log(guildId, GuildLogKind.STRONGHOLD_LOST, "", rank)
  }

  private fun tell(world: World, guildId: Long, notice: Notice, rank: Int) {
    val news = GuildNoticeSMSG(notice, listOf(nameOf(rank)))
    world.query(GuildMembership::class, Account::class).each { _ ->
      if (get<GuildMembership>().guildId == guildId) messages.sendToPlayer(get<Account>().accountId, news)
    }
  }

  private fun log(guildId: Long, kind: GuildLogKind, actorName: String, rank: Int) {
    val name = nameOf(rank)
    asyncJobExecutor.submit(LOG_JOB_KEY) {
      try {
        guilds.logStronghold(guildId, kind, actorName, name)
      } catch (e: GuildException) {
        LOG.warn { "Guild $guildId is gone; its Stronghold news $kind for $name is not logged: ${e.message}" }
      }
    }
  }

  private fun nameOf(rank: Int): String {
    return seeds.awake().firstOrNull { it.rank == rank }?.name.orEmpty()
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    val ATTACK_QUIET: Duration = Duration.ofMinutes(5)

    /** One key for every entry, so they land in the order they happened. */
    const val LOG_JOB_KEY = "stronghold-news"
  }
}
