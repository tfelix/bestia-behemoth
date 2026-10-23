package net.bestia.zone.world.anchor

import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.ActivePlayer
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.residency.ChunkResidency
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.stream.ChunkStreamConfig
import net.bestia.zone.world.stream.ChunkStreamSystem
import net.bestia.zone.world.stream.SlabBudget
import net.bestia.zone.world.stream.ViewVolume
import org.springframework.stereotype.Component as SpringComponent

/**
 * Keeps the ground around every [SimulationAnchor] resident, and publishes where they stand.
 *
 * An anchor holds the same view volume a client is offered, so a bestia far from any player can path and be
 * found by what lives there. Work is only done when an anchor crosses a chunk or the surfaces change.
 */
@SpringComponent
class AnchorResidencySystem(
  private val chunkService: ChunkService,
  private val streamSettings: ChunkStreamConfig,
  private val config: SimulationAnchorConfig,
  private val residency: ChunkResidency,
  private val index: SimulationAnchorIndex,
) : System {

  override val phase = Phase.WORLD

  /** Ordered because both hold residency, which is not thread safe, and teleports move positions there. */
  override val after = setOf(ChunkStreamSystem::class)

  override val reads: ComponentClassSet = setOf(Position::class, SimulationAnchor::class, ActivePlayer::class)

  private val viewVolume = ViewVolume(chunkService, streamSettings)
  private val holdings = HashMap<EntityId, Holding>()

  val holdingAnchors: Int
    get() {
      return holdings.size
    }

  override fun update(world: World, deltaTime: Float) {
    if (!chunkService.isReady) return

    val anchors = anchorsIn(world)
    val holders = mostImportant(anchors)

    releaseAllBut(holders)

    val budget = SlabBudget(config.slabComputationsPerTick)
    for (anchor in holders) {
      index.place(anchor.id, anchor.position)
      holdings.getOrPut(anchor.id) { Holding() }.follow(anchor.chunk, budget)
    }
  }

  private fun anchorsIn(world: World): List<AnchorAt> =
    world.query(Position::class, SimulationAnchor::class).map { id ->
      val position = get<Position>().toVec3L()
      val priority = if (world.has(id, ActivePlayer::class)) AnchorPriority.PLAYER else get<SimulationAnchor>().priority
      AnchorAt(id, position, ChunkCoords.chunkOf(chunkService.config, position), priority)
    }

  private fun mostImportant(anchors: List<AnchorAt>): List<AnchorAt> {
    if (anchors.size <= config.maxHoldingAnchors) return anchors

    return anchors.sortedWith(compareBy({ it.priority }, { it.id })).take(config.maxHoldingAnchors)
  }

  private fun releaseAllBut(holders: List<AnchorAt>) {
    val keep = holders.mapTo(HashSet(holders.size)) { it.id }
    val iterator = holdings.entries.iterator()

    while (iterator.hasNext()) {
      val (id, holding) = iterator.next()
      if (id in keep) continue

      holding.releaseAll()
      index.remove(id)
      iterator.remove()
    }
  }

  private data class AnchorAt(val id: EntityId, val position: Vec3L, val chunk: ChunkPos, val priority: AnchorPriority)

  /** One anchor's held chunks, amended the way a client's manifest is: added in view, released past a margin. */
  private inner class Holding {

    private val held = HashSet<ChunkPos>()
    private var settled: Basis? = null

    fun follow(anchor: ChunkPos, budget: SlabBudget) {
      val basis = Basis(anchor, chunkService.slabEpoch)
      if (settled == basis) return

      val (desired, complete) = viewVolume.chunksAround(anchor, budget)
      val kept = viewVolume.columnsAround(anchor, streamSettings.viewRadiusChunks + streamSettings.releaseMarginChunks)

      for (chunk in desired) {
        if (held.add(chunk)) residency.hold(chunk)
      }

      val left = held.filter { it !in desired && (it.x to it.y) !in kept }
      for (chunk in left) {
        held.remove(chunk)
        residency.release(chunk)
      }

      settled = if (complete) basis else null
    }

    fun releaseAll() {
      held.forEach { residency.release(it) }
      held.clear()
    }
  }

  private data class Basis(val anchor: ChunkPos, val slabEpoch: Long)
}
