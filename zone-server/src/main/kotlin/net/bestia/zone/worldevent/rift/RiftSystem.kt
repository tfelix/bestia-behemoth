package net.bestia.zone.worldevent.rift

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.townsfolk.rumour.RumourKind
import net.bestia.zone.townsfolk.rumour.RumourService
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaField
import net.bestia.zone.world.mana.ManaGround
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.time.BestiaClock
import net.bestia.zone.worldevent.persistence.StoredRift
import net.bestia.zone.worldevent.rift.RiftLifecycle.News
import org.springframework.stereotype.Component

/**
 * Moves every rift on once a minute, keeps a gate standing at each open one and lets its hordes out. Touches no component in the
 * step; gates and rumours go in a deferred block, because other systems touch those beside this one.
 */
@Component
class RiftSystem(
  private val rifts: Rifts,
  private val field: ManaField,
  ground: ManaGround,
  config: RiftConfig,
  private val clock: BestiaClock,
  private val chunks: ChunkService,
  private val worldService: WorldService,
  private val rumours: RumourService,
  private val hordes: RiftHordes,
) : System {

  private val lifecycle = RiftLifecycle(config, RiftField(field, ground), realSecondsPerBestiaSecond = 1.0 / clock.speedFactor)

  /** Gate entities by rift. Not persisted: a gate is put up again after a restart. */
  private val gates = HashMap<Long, EntityId>()

  override val phase = Phase.UPKEEP

  override val schedule: Schedule = Schedule.EverySeconds(60f)

  override val reads: ComponentClassSet = emptySet()

  override val writes: ComponentClassSet = emptySet()

  override fun update(world: World, deltaTime: Float) {
    val active = rifts.active()
    if (active.isEmpty() && gates.isEmpty()) return

    val now = clock.now().absoluteSecond
    val news = synchronized(field) {
      val heard = active.mapNotNull { rift -> lifecycle.step(rift, now, deltaTime.toDouble())?.let { rift to it } }
      lifecycle.merge(active)
      heard
    }

    world.defer {
      keepGates(world, active)
      hordes.tend(world, active, now)
      rifts.saveAll(active)
      news.forEach { (rift, what) -> tell(rift, what) }
    }
  }

  private fun keepGates(world: World, rifts: List<StoredRift>) {
    val open = rifts.filter { it.phase == StoredRift.Phase.OPEN }.associateBy { it.id }

    gates.entries.removeIf { (riftId, gate) ->
      val gone = riftId !in open || !world.isAlive(gate)
      if (gone && world.isAlive(gate)) world.destroy(gate)
      gone
    }

    open.values.filter { it.id !in gates }.forEach { rift ->
      val at = groundAt(rift) ?: return@forEach
      gates[rift.id] = world.createEntity { id ->
        add(id, Position.fromVec3(at))
        add(id, EntityVisual(VisualKind.STRUCTURE, StaticEntityKind.RIFT_GATE.ordinal.toLong()))
        add(id, RiftGate(rift.id))
      }
    }
  }

  private fun tell(rift: StoredRift, what: News) {
    val kind = when (what) {
      News.OPENED -> RumourKind.RIFT_OPENED
      News.COLLAPSED -> RumourKind.RIFT_COLLAPSED
      News.DISSOLVED -> return
    }
    val voxel = worldService.config.voxelSize
    rumours.post(kind, (rift.x / voxel).toLong(), (rift.y / voxel).toLong(), strength = 0.9)
  }

  private fun groundAt(rift: StoredRift): Vec3L? {
    val config = worldService.config
    val voxelX = Math.floor(rift.x / config.voxelSize).toLong()
    val voxelY = Math.floor(rift.y / config.voxelSize).toLong()
    val elevation = chunks.surfaceElevationAt(voxelX, voxelY) ?: return null
    return Vec3L(voxelX, voxelY, ChunkCoords.standingZ(config, elevation))
  }
}
