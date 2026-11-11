package net.bestia.zone.crafting.station

import net.bestia.zone.bestia.diary.BestiaNews
import net.bestia.zone.bestia.diary.BestiaHappened
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.ecs.CarryCapacity
import net.bestia.zone.item.ecs.CarryCheck
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ObtainItemIntentSystem
import net.bestia.zone.item.mining.MiningSkill
import net.bestia.zone.item.mining.ShaftOre
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.prop.station.WorkAssignment
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.anchor.SimulationAnchor
import org.springframework.stereotype.Component as SpringComponent

/**
 * Digs the ore below every mining shaft a bestia works, while it stands at the shaft. The ore goes into its bag.
 *
 * How deep and how fast it digs is the master's Mining. A shaft with nothing left in reach lets its miner go.
 */
@SpringComponent
class ShaftWorkSystem(
  private val structures: PlayerStructureRegistry,
  private val workers: StationWorkers,
  private val profiles: CrafterProfiles,
  private val connections: ConnectionInfoService,
  private val mining: MiningSkill,
  private val ore: ShaftOre,
  private val news: BestiaNews = BestiaNews { },
) : System {

  override val phase = Phase.ITEMS

  // Both move workers on and off their work. Before the grant path, like the stations, for what reads the bag.
  override val after = setOf(StationWorkSystem::class)
  override val before = setOf(ObtainItemIntentSystem::class)
  override val reads: ComponentClassSet =
    setOf(Position::class, Inventory::class, CarryCapacity::class, AiAgent::class, Master::class, KnownSkills::class)
  override val writes: ComponentClassSet = setOf(WorkAssignment::class, SimulationAnchor::class)

  /** Shaft id -> seconds of labour put in towards the next carve. Below zero after a carve that took much ore. */
  private val labour = HashMap<Long, Float>()

  override fun update(world: World, deltaTime: Float) {
    val shafts = structures.all().filter { it.kind == StaticEntityKind.MINING_SHAFT && !it.isUnderConstruction }
    val standing = shafts.map { it.id }.toSet()
    labour.keys.retainAll(standing)
    ore.keepOnly(standing)

    shafts.filter { it.workerBestiaId != null }.forEach { shaft -> work(world, shaft, deltaTime) }
  }

  private fun work(world: World, shaft: StructureEntry, deltaTime: Float) {
    val miner = workers.entityOf(shaft.id)?.takeIf { world.get(it, WorkAssignment::class)?.structureId == shaft.id }
    if (miner == null || !isNear(world, miner, shaft)) return

    val level = miningLevelOf(world, shaft) ?: return
    val reach = ShaftOre.reachAt(level)
    if (reach == null || !ore.hasOre(shaft.id, shaft.position, reach)) {
      letGo(world, shaft)
      return
    }
    if (!CarryCheck.fits(world, miner, ROOM_TO_DIG)) {
      askToEmptyBag(world, miner)
      return
    }

    val secondsPerVoxel = secondsPerVoxelAt(level)
    val done = (labour[shaft.id] ?: 0f) + deltaTime
    if (done < secondsPerVoxel) {
      labour[shaft.id] = done
      return
    }

    val dug = ore.dig(shaft.id, shaft.position, reach, shaft.ownerAccountId ?: NO_ACCOUNT, miner)
    labour[shaft.id] = done - dug * secondsPerVoxel
  }

  /** A shaft dug out, or a master who cannot mine: its miner goes back to its area and looks for another. */
  private fun letGo(world: World, shaft: StructureEntry) {
    shaft.workerBestiaId?.let { worker ->
      news.tell(
        BestiaHappened(this, shaft.ownerMasterId, worker, BestiaHappened.Kind.WORK_PAUSED, place = shaft.kind.name, reason = NO_ORE)
      )
    }
    workers.unassign(world, shaft.id)
    labour.remove(shaft.id)
  }

  /** The master's Mining, kept for while they are away. Null while it is still loading. */
  private fun miningLevelOf(world: World, shaft: StructureEntry): Int? {
    playingMasterOf(shaft)?.let { profiles.remember(world, it) }

    return profiles.knowledgeOf(shaft.ownerMasterId)?.let { mining.levelOf(it.skills) }
  }

  private fun playingMasterOf(shaft: StructureEntry): EntityId? {
    val accountId = shaft.ownerAccountId ?: return null
    if (!connections.isPlaying(accountId, shaft.ownerMasterId)) return null

    return connections.getSelectedMasterEntityId(accountId)
  }

  private fun isNear(world: World, workerId: EntityId, shaft: StructureEntry): Boolean {
    val at = world.get(workerId, Position::class)?.toVec3L() ?: return false

    return at.distance(shaft.position) <= PlayerStructureService.RANGE_TILES
  }

  /** Tells a miner its bag is too full for the next carve, so it carries the ore to the chest first. */
  private fun askToEmptyBag(world: World, workerId: EntityId) {
    val memory = world.get(workerId, AiAgent::class)?.memory ?: return
    memory.set(BestiaDomain.NO_ROOM_FOR_PRODUCT, true, BestiaDomain.NO_ROOM_FOR_PRODUCT.retain)
  }

  companion object {
    /** Labour for one ore voxel, about two lumps, before Mining. Slow, because what comes out is the world's money. */
    const val BASE_SECONDS_PER_VOXEL = 300f

    /** Each level of Mining takes this share off [BASE_SECONDS_PER_VOXEL]. */
    const val TIME_CUT_PER_LEVEL = 0.1f

    /**
     * Room a miner needs in its bag before the next carve: two lumps of gold. A carve through a rich vein can bring
     * more, and what does not fit falls at its feet.
     */
    const val ROOM_TO_DIG = 700

    /** Why a miner stopped, in its diary: nothing left in reach. */
    const val NO_ORE = "NO_ORE"

    /** A shaft whose owner has no account on record yet. Only what the carve is logged under. */
    private const val NO_ACCOUNT = 0L

    fun secondsPerVoxelAt(miningLevel: Int): Float {
      return BASE_SECONDS_PER_VOXEL * (1f - TIME_CUT_PER_LEVEL * miningLevel).coerceAtLeast(TIME_CUT_PER_LEVEL)
    }
  }
}
