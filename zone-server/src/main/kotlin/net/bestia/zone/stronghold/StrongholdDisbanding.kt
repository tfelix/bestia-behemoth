package net.bestia.zone.stronghold

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.guild.GuildDisbanding
import net.bestia.zone.identity.ecs.GuildOwned
import net.bestia.zone.item.container.ItemHolder
import net.bestia.zone.item.persistence.GuildStorageRepository
import net.bestia.zone.message.AnnouncementSMSG
import net.bestia.zone.message.AnnouncementSMSG.Announcement
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.stronghold.ecs.Empyrite
import net.bestia.zone.stronghold.persistence.StrongholdRecord.State
import org.springframework.stereotype.Component

/**
 * A disbanded guild's Strongholds stand empty: steward and watchers leave, nobody may use the Stronghold, and the next
 * guild to break the Empyrite takes it as in any capture. Everyone online hears of it. A site that was not finished
 * is simply gone, and its seed is free again. A guild whose storage still holds goods may not disband: nobody
 * would be left to take them.
 */
@Component
class StrongholdDisbanding(
  private val strongholds: StrongholdRegistry,
  private val seeds: EmpyriteSeedRegistry,
  private val sites: EmpyriteSites,
  private val stewards: Stewards,
  private val watchers: Watchers,
  private val storages: GuildStorages,
  private val storageRepository: GuildStorageRepository,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val messages: OutMessageProcessor,
) : GuildDisbanding {

  override fun refusal(world: World, guildId: Long): OpError? {
    return if (storages.contentsOf(guildId).isEmpty()) null else OpError.GUILD_DISBAND_STORAGE_NOT_EMPTY
  }

  override fun disbanded(world: World, guildId: Long, guildName: String) {
    for (stronghold in strongholds.heldBy(guildId).toList()) {
      when (stronghold.state) {
        State.SITE -> sites.abandon(world, stronghold)
        State.STANDING -> leaveEmpty(world, stronghold, guildName)
      }
    }

    storages.forget(guildId)
    asyncJobExecutor.submit(ItemHolder.GuildStorage(guildId)) { storageRepository.deleteById(guildId) }
  }

  private fun leaveEmpty(world: World, stronghold: Stronghold, guildName: String) {
    stewards.dismiss(world, stronghold.rank)
    watchers.dismissAll(world, stronghold.rank)
    strongholds.put(
      stronghold.copy(guildId = Stronghold.UNHELD, hasSteward = false, watchers = emptyMap(), watcherStrength = 0)
    )
    world.query(Empyrite::class).each { id ->
      if (get<Empyrite>().rank == stronghold.rank) world.add(id, GuildOwned(Stronghold.UNHELD))
    }

    val name = seeds.awake().firstOrNull { it.rank == stronghold.rank }?.name.orEmpty()
    messages.sendToAllConnected(AnnouncementSMSG(Announcement.STRONGHOLD_UNHELD, listOf(guildName, name)))
    LOG.info { "Guild ${stronghold.guildId} disbanded; its Stronghold at seed ${stronghold.rank} stands empty" }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
