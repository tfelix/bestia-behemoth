package net.bestia.zone.stronghold

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.WorldReserve
import net.bestia.zone.entity.GameMetrics.CoinSource
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.identity.ecs.GuildOwned
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.skill.SkillId
import net.bestia.zone.stronghold.ecs.EmpyriteSeedSprout
import net.bestia.zone.stronghold.persistence.StrongholdRecord
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service
import java.time.Clock
import kotlin.math.hypot

/**
 * A member plants the Empyrite at a seed: they pay, and an Empyrite site of their guild goes up where the sprout
 * stood. Tick thread only.
 */
@Service
class StrongholdFounding(
  private val seeds: EmpyriteSeedRegistry,
  private val sprouts: EmpyriteSeedSprouts,
  private val discoveries: EmpyriteSeedDiscoveries,
  private val strongholds: StrongholdRegistry,
  private val structures: PlayerStructureService,
  private val inventoryService: InventoryService,
  private val commodities: CommodityItems,
  private val reserve: WorldReserve,
  private val worldService: WorldService,
  private val world: WorldView,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val messages: OutMessageProcessor,
  private val config: StrongholdConfig,
  private val clock: Clock = Clock.systemUTC(),
) {

  /** The first click on a sprout: what planting would cost, or why it cannot be done. */
  fun offer(world: World, actorId: EntityId, sproutId: EntityId): OpError? {
    val seed = seedOf(world, sproutId) ?: return null
    refusal(world, actorId, seed)?.let { return it }

    val accountId = world.get(actorId, Account::class)?.accountId ?: return null
    messages.sendToPlayer(accountId, EmpyritePlantOfferSMSG(sproutId, seed.name, config.plantCoins, config.siteBuildMinutes))

    return null
  }

  /** The confirmed click. @return the refusal to send back, or null once the site stands */
  fun plant(world: World, actorId: EntityId, sproutId: EntityId): OpError? {
    val seed = seedOf(world, sproutId) ?: return OpError.REQUEST_REFUSED
    refusal(world, actorId, seed)?.let { return it }

    val master = world.get(actorId, Master::class) ?: return OpError.REQUEST_REFUSED
    val accountId = world.get(actorId, Account::class)?.accountId ?: return OpError.REQUEST_REFUSED
    val guildId = world.get(actorId, GuildMembership::class)?.guildId ?: return OpError.REQUEST_REFUSED
    val at = world.get(sproutId, Position::class)?.toVec3L() ?: return OpError.REQUEST_REFUSED

    if (!pay(world, actorId, master.masterId)) return OpError.STRONGHOLD_CANNOT_AFFORD

    val buildSeconds = config.siteBuildMinutes * 60f
    val siteId = structures.beginConstruction(
      world, StaticEntityKind.EMPYRITE_SITE, master.masterId, accountId, at, 0f, buildSeconds
    )
    val structureId = siteId?.let { world.get(it, PlayerStructureIdentity::class)?.structureId }
    if (siteId == null || structureId == null) {
      LOG.error { "Master ${master.masterId} paid for an Empyrite site at seed ${seed.rank} that could not go up" }
      return OpError.REQUEST_REFUSED
    }

    world.add(siteId, GuildOwned(guildId))
    sprouts.uproot(world, seed.rank)
    discoveries.discover(master.masterId, seed.rank)

    val now = clock.instant()
    strongholds.put(Stronghold(seed.rank, guildId, StrongholdRecord.State.SITE, structureId, now, now))
    LOG.info { "Master ${master.masterId} of guild $guildId planted an Empyrite site at seed ${seed.rank} (${seed.name})" }

    return null
  }

  private fun refusal(world: World, actorId: EntityId, seed: EmpyriteSeed): OpError? {
    val membership = world.get(actorId, GuildMembership::class)
    val mayPlant = membership != null && membership.mayUseGuildSkills &&
      membership.skillLevel(SkillId.OFFICIAL_GUILD_APPROVAL.name) > 0

    return when {
      membership == null || !mayPlant -> OpError.STRONGHOLD_NOT_ALLOWED
      strongholds.heldBy(membership.guildId).isNotEmpty() -> OpError.STRONGHOLD_ALREADY_HELD
      strongholds.on(seed.rank) != null -> OpError.REQUEST_REFUSED
      !isOnCore(world, actorId, seed) -> OpError.STRONGHOLD_NOT_ON_CORE
      coinsHeld(world, actorId) < config.plantCoins -> OpError.STRONGHOLD_CANNOT_AFFORD
      else -> null
    }
  }

  private fun seedOf(world: World, sproutId: EntityId): EmpyriteSeed? {
    val rank = world.get(sproutId, EmpyriteSeedSprout::class)?.rank ?: return null

    return seeds.awake().firstOrNull { it.rank == rank }
  }

  private fun isOnCore(world: World, actorId: EntityId, seed: EmpyriteSeed): Boolean {
    val at = world.get(actorId, Position::class)?.toVec3L() ?: return false
    val voxel = worldService.config.voxelSize

    return hypot(at.x * voxel - seed.x, at.y * voxel - seed.y) <= config.coreRadiusMetres
  }

  private fun coinsHeld(world: World, actorId: EntityId): Long {
    val coin = commodities.coinItemId() ?: return 0
    val inventory = world.get(actorId, Inventory::class) ?: return 0

    return inventory.getItems().filter { it.itemId == coin && it.isStackable }.sumOf { it.amount.toLong() }
  }

  /**
   * Takes the coins off the live bag now and out of the database in a job, as a shop trade does. They go back to
   * the world reserve once the database agrees.
   */
  private fun pay(world: World, actorId: EntityId, masterId: Long): Boolean {
    val coins = config.plantCoins.toInt()
    if (coins == 0) return true

    val coin = commodities.coinItemId() ?: return false
    val inventory = world.get(actorId, Inventory::class) ?: return false
    inventory.takeAcrossPiles(coin, coins) ?: return false

    asyncJobExecutor.submit(masterId) {
      if (inventoryService.consumeAll(masterId, listOf(coin to coins))) {
        this.world.read { reserve.charge(-coins.toDouble(), CoinSource.STRONGHOLD) }
      } else {
        // The live bag is put right by the next login; the site already stands, so the planting was free.
        LOG.warn { "Master $masterId planted an Empyrite site, but the database still holds the $coins coins" }
      }
    }

    return true
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
