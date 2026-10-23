package net.bestia.zone.world.vicinity

import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.stream.ChunkStreamSystem
import org.springframework.stereotype.Component as SpringComponent

/**
 * Keeps every [Vicinity] centred on its entity and tells the [VicinitySense]s which columns came near.
 *
 * A standing entity costs one chunk lookup per tick; only crossing into another chunk does any work.
 */
@SpringComponent
class VicinitySystem(
  private val chunkService: ChunkService,
  private val index: VicinityIndex,
  private val senses: List<VicinitySense>,
) : System {

  /** After the walk, so a sense learns where an entity got to this tick. */
  override val phase = Phase.WORLD

  /** Teleports move positions there. */
  override val after = setOf(ChunkStreamSystem::class)

  override val reads: ComponentClassSet = setOf(Position::class, Vicinity::class) + senses.flatMap { it.reads }

  override fun update(world: World, deltaTime: Float) {
    if (!chunkService.isReady) return

    val present = HashSet<EntityId>()

    world.query(Position::class, Vicinity::class).each { id ->
      present.add(id)

      val center = columnOf(get<Position>())
      if (index.centerOf(id) == center) return@each

      val entered = index.cover(id, center, columnsAround(center, get<Vicinity>().radiusChunks))
      if (entered.isNotEmpty()) senses.forEach { it.onEntered(world, id, entered) }
    }

    index.forgetAllBut(present)
    senses.forEach { it.afterUpdate(world) }
  }

  private fun columnOf(position: Position): ChunkColumn {
    val chunk = ChunkCoords.chunkOf(chunkService.config, position.toVec3L())
    return ChunkColumn(chunk.x, chunk.y)
  }

  /** Normalised, so a vicinity across the world seam covers the columns on the far side. */
  private fun columnsAround(center: ChunkColumn, radius: Int): Set<ChunkColumn> {
    val columns = HashSet<ChunkColumn>((2 * radius + 1) * (2 * radius + 1))

    for (dy in -radius..radius) {
      for (dx in -radius..radius) {
        val chunk = chunkService.normalise(ChunkPos(center.x + dx, center.y + dy, 0))
        columns.add(ChunkColumn(chunk.x, chunk.y))
      }
    }

    return columns
  }
}
