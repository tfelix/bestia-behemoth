package net.bestia.zone.stronghold

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.StorageOpenedSMSG
import net.bestia.zone.item.container.ItemHolder
import net.bestia.zone.item.ecs.ExchangeReach
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.SlotStorage
import net.bestia.zone.item.ecs.toLiveInventory
import net.bestia.zone.item.persistence.GuildStorageRepository
import net.bestia.zone.item.stat.ItemStatCatalog
import net.bestia.zone.master.ecs.MasterVisual
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.skill.SkillId
import net.bestia.zone.stronghold.ecs.Steward
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Every guild's one storage, live, opened at any of its stewards. Only one member has it open at a time; the
 * steward keeps the guild's [Inventory] and a [SlotStorage] that lets that member, and only that member, reach in
 * as far as their rank allows. A guild without a steward cannot open it: its storage is sealed.
 *
 * Tick thread only after [loadAll].
 */
@Service
class GuildStorages(
  private val repository: GuildStorageRepository,
  private val itemStats: ItemStatCatalog,
  private val strongholds: StrongholdRegistry,
  private val messages: OutMessageProcessor,
  private val config: StrongholdConfig,
) {

  /** Who has a guild's storage open, and at which steward. */
  private data class Opening(val stewardId: EntityId, val userId: EntityId, val masterId: Long)

  private val contentsByGuild = HashMap<Long, Inventory>()
  private val openings = HashMap<Long, Opening>()

  /** Boot only. Guilds are few, so every storage is loaded, as chests are. */
  @Transactional(readOnly = true)
  fun loadAll() {
    repository.findAll().forEach { contentsByGuild[it.guildId] = it.container.toLiveInventory(itemStats) }

    LOG.info { "Loaded the storages of ${contentsByGuild.size} guild(s)" }
  }

  /** What the guild's storage holds now. One live bag per guild, whichever steward keeps it. */
  fun contentsOf(guildId: Long): Inventory {
    return contentsByGuild.getOrPut(guildId) { Inventory(mutableListOf()) }
  }

  /** A member clicked a steward of their guild: the storage opens in their inventory. */
  fun open(world: World, userId: EntityId, stewardId: EntityId): OpError? {
    val rank = world.get(stewardId, Steward::class)?.rank ?: return OpError.REQUEST_REFUSED
    val guildId = strongholds.on(rank)?.guildId ?: return OpError.REQUEST_REFUSED
    val membership = world.get(userId, GuildMembership::class)?.takeIf { it.guildId == guildId }
      ?: return OpError.GUILD_STORAGE_NOT_YOUR_GUILDS
    val masterId = world.get(userId, Master::class)?.masterId ?: return OpError.REQUEST_REFUSED
    val accountId = world.get(userId, Account::class)?.accountId ?: return OpError.REQUEST_REFUSED
    refusal(world, membership, userId, stewardId)?.let { return it }

    val other = openings[guildId]?.takeIf { it.userId != userId && isStillOpen(world, it) }
    if (other != null) {
      val name = world.get(other.userId, MasterVisual::class)?.name.orEmpty()
      messages.sendToPlayer(accountId, OperationErrorSMSG(OpError.GUILD_STORAGE_IN_USE, listOf(name)))
      return null
    }

    openings[guildId]?.let { previous -> if (previous.stewardId != stewardId) detach(world, previous.stewardId) }
    val contents = contentsOf(guildId)
    val slots = slotsOf(membership)
    val use = SlotStorage.Use(mayPut = membership.mayDeposit, mayTake = membership.mayTake)

    world.add(stewardId, contents)
    world.add(stewardId, SlotStorage(ItemHolder.GuildStorage(guildId), slots, mapOf(masterId to use), setOf(accountId)))
    openings[guildId] = Opening(stewardId, userId, masterId)

    messages.sendToPlayer(accountId, StorageOpenedSMSG(stewardId, slots, StorageOpenedSMSG.Kind.GUILD_STORAGE))
    contents.markDirty()

    return null
  }

  /** Closes every storage whose member walked away, logged out or fell, so another member may open it. */
  fun closeLeft(world: World) {
    val left = openings.filterValues { !isStillOpen(world, it) }
    left.forEach { (guildId, opening) ->
      openings.remove(guildId)
      world.get(opening.stewardId, SlotStorage::class)?.let { storage ->
        storage.users = emptyMap()
        storage.viewers = emptySet()
      }
    }
  }

  /**
   * Room from Guild Storage, plus the Storage track of every Stronghold the guild holds. A storage over its limit
   * keeps what it holds.
   */
  fun slotsOf(membership: GuildMembership): Int {
    val skillLevel = membership.skillLevel(SkillId.GUILD_STORAGE.name)
    val trackLevels = strongholds.heldBy(membership.guildId).sumOf { it.progressOf(StrongholdTrack.STORAGE).level }

    return config.storageSlotsPerSkillLevel * skillLevel + config.storageSlotsPerStorageLevel * trackLevels
  }

  private fun refusal(world: World, membership: GuildMembership, userId: EntityId, stewardId: EntityId): OpError? {
    return when {
      membership.skillLevel(SkillId.GUILD_STORAGE.name) <= 0 -> OpError.GUILD_STORAGE_NOT_LEARNED
      !membership.mayDeposit && !membership.mayTake -> OpError.GUILD_STORAGE_NOT_ALLOWED
      !ExchangeReach.within(world, userId, stewardId) -> OpError.GUILD_STORAGE_OUT_OF_REACH
      else -> null
    }
  }

  private fun isStillOpen(world: World, opening: Opening): Boolean {
    return world.isAlive(opening.stewardId) &&
      world.get(opening.userId, Master::class)?.masterId == opening.masterId &&
      ExchangeReach.within(world, opening.userId, opening.stewardId)
  }

  /** The guild's goods move to another steward; this one keeps nothing. */
  private fun detach(world: World, stewardId: EntityId) {
    if (!world.isAlive(stewardId)) return

    world.remove(stewardId, SlotStorage::class)
    world.remove(stewardId, Inventory::class)
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
