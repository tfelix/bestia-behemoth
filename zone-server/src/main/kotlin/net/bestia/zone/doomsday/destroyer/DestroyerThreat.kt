package net.bestia.zone.doomsday.destroyer

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.voxel.PropId
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.doomsday.DoomAvatarFall
import net.bestia.zone.doomsday.DoomFate
import net.bestia.zone.doomsday.DoomKind
import net.bestia.zone.doomsday.DoomShape
import net.bestia.zone.doomsday.DoomStage
import net.bestia.zone.doomsday.DoomThreat
import net.bestia.zone.doomsday.destroyer.DestroyerTowns.Target
import net.bestia.zone.doomsday.persistence.StoredDestroyer
import net.bestia.zone.doomsday.persistence.StoredDestroyerRepository
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.EventCreature
import net.bestia.zone.entity.ecs.WorldObjectIdentity
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.place.ecs.ProvinceService
import net.bestia.zone.prop.PropRemoval
import net.bestia.zone.prop.WorldObjectDivergenceRegistry
import net.bestia.zone.prop.WorldObjectResidencyService
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.anchor.AnchorPriority
import net.bestia.zone.world.anchor.SimulationAnchor
import net.bestia.zone.worldboss.WorldBossSealService
import net.bestia.zone.worldboss.WorldBossSealService.SealCrystal
import net.bestia.zone.worldevent.EventGround
import org.springframework.stereotype.Component
import java.time.Clock
import kotlin.math.hypot
import kotlin.random.Random

/**
 * The Destroyer: a sealed world boss breaks free at onset and marches from town to town. It razes each town it reaches,
 * heals fully and grows stronger, which brings the end closer. Start towns are spared, and no trap holds it.
 *
 * Players hold it back by bringing it down: that holds the end off, and it rises again at its lair a few days later,
 * one town stronger, unless a ritual at the lair renews the seal for longer. In the Last Stand it is the Avatar
 * itself. It marches on the players' towns as on the world's.
 */
@Component
class DestroyerThreat(
  private val config: DestroyerConfig,
  private val rows: StoredDestroyerRepository,
  private val fall: DestroyerFall,
  private val seal: DestroyerSeal,
  private val seals: WorldBossSealService,
  private val world: WorldView,
  private val spawner: BestiaEntitySpawner,
  private val statusEffects: StatusEffectService,
  private val towns: DestroyerTowns,
  private val provinces: ProvinceService,
  private val removal: PropRemoval,
  private val residency: WorldObjectResidencyService,
  private val divergence: WorldObjectDivergenceRegistry,
  private val eventGround: EventGround,
  private val worldService: WorldService,
  private val clock: Clock = Clock.systemUTC(),
) : DoomThreat {

  override val kind = DoomKind.DESTROYER

  override val shape = DoomShape.ACTOR

  private val lairs: List<SealCrystal> by lazy { seals.all().filterNot { provinces.isSanctuaryAt(it.at.x, it.at.y) } }

  /** The Destroyer standing in the world now. Not stored: after a restart it rises again where it last stood. */
  @Volatile
  private var live: EntityId? = null

  @Volatile
  private var lastStand = false

  @Volatile
  private var pressure = 0.0

  @Volatile
  private var containment = 0.0

  override fun fits(): Boolean {
    return lairs.isNotEmpty()
  }

  override fun origin(random: Random): Vec2d {
    return lairs.random(random).at
  }

  override fun pressure(): Double {
    return pressure
  }

  override fun containment(): Double {
    return containment
  }

  override fun step(fate: DoomFate, realSeconds: Double): Double {
    if (fate.stage < DoomStage.ONSET) {
      return 0.0
    }

    val row = rows.findById(StoredDestroyer.ONLY).orElse(null) ?: breakFree(fate)
    var push = 0.0
    if (fall.take() && !row.isFallen) {
      fell(row)
      push += config.defeatPush
    }
    if (seal.take() && row.isFallen) {
      row.returnsAtDay = maxOf(row.returnsAtDay!!, today() + config.resealDays)
      LOG.info { "A ritual renewed the Destroyer's seal: it stays down ${config.resealDays} days" }
    }
    if (row.isFallen && today() >= row.returnsAtDay!!) {
      rise(row)
    }
    seal.watch(if (row.isFallen) eventGround.grounded(row.lairX, row.lairY) else null)
    if (!row.isFallen) {
      push += march(row)
    }

    val left = world.read { towns.countLeft(row.razedTownList()) }
    pressure = row.razed.toDouble() / (row.razed + left).coerceAtLeast(1)
    containment = containmentOf(row)
    rows.save(row)
    return push
  }

  /** The Destroyer is the Avatar of the Last Stand, at the strength its razed towns gave it. A fallen one rises now. */
  override fun onLastStand(fate: DoomFate) {
    lastStand = true
    val row = rows.findById(StoredDestroyer.ONLY).orElse(null) ?: return
    if (row.isFallen) {
      row.returnsAtDay = today()
      rows.save(row)
    }
    live?.let { id ->
      world.modify(id) {
        add(it, avatarTag())
        get(it, Health::class)?.let { health -> health.current = health.max }
      }
    }
    LOG.warn { "The Destroyer stands as the Avatar of the Last Stand" }
  }

  /** The seal nearest the doom's origin breaks, and the boss behind it walks out as the Destroyer. */
  private fun breakFree(fate: DoomFate): StoredDestroyer {
    val seal = seals.all().minBy { hypot(it.at.x - fate.originX, it.at.y - fate.originY) }
    world.read { breakSeal(this, seal) }
    LOG.warn { "The seal at (${seal.at.x}, ${seal.at.y}) broke: the Destroyer walks" }
    return StoredDestroyer(bestiaId = seal.bossId, lairX = seal.at.x, lairY = seal.at.y, x = seal.at.x, y = seal.at.y)
  }

  private fun breakSeal(world: World, seal: SealCrystal) {
    val goneForGood = divergence.of(seal.propId)?.let { it.resumeAt == null } == true
    if (goneForGood) {
      return
    }
    val chunkSize = worldService.config.chunkSize.toLong()
    val chunkX = Math.floorDiv(PropId.cellXOf(seal.propId), chunkSize).toInt()
    val chunkY = Math.floorDiv(PropId.cellYOf(seal.propId), chunkSize).toInt()
    val resident = residency.entitiesIn(chunkX, chunkY).firstOrNull { entity ->
      world.get(entity, WorldObjectIdentity::class)?.propId == seal.propId
    }
    if (resident != null) {
      removal.removed(world, resident, PropRemoval.Cause.GONE_FOR_GOOD)
    } else {
      removal.removedUnseen(seal.propId, StaticEntityKind.FIXTURE_SEAL_CRYSTAL, PropRemoval.Cause.GONE_FOR_GOOD)
    }
  }

  private fun fell(row: StoredDestroyer) {
    live = null
    row.lastDefeatDay = today()
    row.returnsAtDay = today() + config.returnDays
    setTarget(row, null)
    if (lastStand) {
      lastStand = false
    }
    LOG.info { "The Destroyer fell. It rises again in ${config.returnDays} days" }
  }

  /** Back at its lair, one town stronger. */
  private fun rise(row: StoredDestroyer) {
    row.returnsAtDay = null
    row.stack++
    row.x = row.lairX
    row.y = row.lairY
    LOG.warn { "The Destroyer rose again at its lair, stack ${row.stack}" }
  }

  /** Walks on to the nearest town it has not razed, and razes the one it stands in. */
  private fun march(row: StoredDestroyer): Double {
    val id = live?.takeIf { world.read { isAlive(it) } } ?: spawn(row)?.also { live = it } ?: return 0.0

    return world.modify(id) {
      val metres = worldService.config.voxelSize
      val position = get(it, Position::class) ?: return@modify 0.0
      row.x = position.x * metres
      row.y = position.y * metres

      var push = 0.0
      val target = targetOf(row)
      if (target != null && towns.metresTo(target, row.x, row.y) <= config.arriveMetres) {
        raze(this, it, row, target)
        push = config.razePush
      }

      val next = targetOf(row)
      setTarget(row, next)
      next?.let { town -> headFor(this, it, town) }
      push
    } ?: 0.0
  }

  private fun spawn(row: StoredDestroyer): EntityId? {
    val at = eventGround.grounded(row.x, row.y) ?: return null
    return world.read {
      val id = spawner.spawnMob(this, row.bestiaId, at, persistent = false, homePosition = at)
      add(id, if (lastStand) avatarTag() else EventCreature(DestroyerFall.EVENT, 0))
      add(id, SimulationAnchor(priority = AnchorPriority.TRAVELLER, atRest = AnchorPriority.TRAVELLER))
      val baseHp = get(id, Health::class)?.max ?: 1
      empower(this, id, row.stack, baseHp)
      id
    }
  }

  private fun raze(world: World, id: EntityId, row: StoredDestroyer, town: Target) {
    towns.raze(world, town)
    if (town is Target.Settlement) {
      row.razedTowns = (row.razedTownList() + town.index).joinToString(",")
    }
    row.razed++

    val baseHp = (world.get(id, Health::class)?.max ?: 1) / hpMultiplierOf(row.stack)
    row.stack++
    empower(world, id, row.stack, baseHp)
    LOG.warn { "The Destroyer razed $town and grew to stack ${row.stack}" }
  }

  /** Its `DESTROYER` status at [stack], and its health grown to match, full. */
  private fun empower(world: World, id: EntityId, stack: Int, baseHp: Int) {
    statusEffects.applyEffect(world, id, StatusEffectId.DESTROYER, level = stack)
    world.get(id, Health::class)?.let { health ->
      health.max = baseHp * hpMultiplierOf(stack)
      health.current = health.max
    }
  }

  private fun hpMultiplierOf(stack: Int): Int {
    return config.hpMultiplier + config.hpMultiplierPerTown * (stack - 1)
  }

  private fun headFor(world: World, id: EntityId, town: Target) {
    val home = towns.groundAt(town) ?: return
    val memory = world.get(id, AiAgent::class)?.memory ?: return
    memory.set(BestiaDomain.HOME_POSITION, home, Blackboard.PERMANENT)
    memory.set(BestiaDomain.WANDER_RADIUS, config.wanderVoxels, Blackboard.PERMANENT)
  }

  /** The town it marches on, or the nearest one left once that one is gone. */
  private fun targetOf(row: StoredDestroyer): Target? {
    val razed = row.razedTownList()
    val current = row.targetSettlement?.let { Target.Settlement(it) } ?: row.targetTown?.let { Target.PlayerTown(it) }
    return current?.takeIf { towns.isTarget(it, razed) } ?: towns.nearest(row.x, row.y, razed)
  }

  private fun setTarget(row: StoredDestroyer, target: Target?) {
    row.targetSettlement = (target as? Target.Settlement)?.index
    row.targetTown = (target as? Target.PlayerTown)?.id
  }

  /** Full while it lies fallen, then fading out over the days after its last defeat. */
  private fun containmentOf(row: StoredDestroyer): Double {
    if (row.isFallen) {
      return 1.0
    }
    val since = row.lastDefeatDay ?: return 0.0
    return (1.0 - (today() - since) / config.containmentDays).coerceIn(0.0, 1.0)
  }

  private fun avatarTag(): EventCreature {
    return EventCreature(DoomAvatarFall.EVENT, DestroyerFall.AVATAR_SUBJECT)
  }

  private fun today(): Double {
    return clock.millis() / MILLIS_PER_DAY
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    const val MILLIS_PER_DAY = 86_400_000.0
  }
}
