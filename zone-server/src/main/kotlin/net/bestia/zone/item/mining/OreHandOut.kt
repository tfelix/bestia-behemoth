package net.bestia.zone.item.mining

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.ecs.ObtainItemIntent
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.stream.CarveYield
import net.bestia.zone.world.stream.ChunkService
import org.springframework.stereotype.Component

/** Gives a carver the ore their carve broke out, as item intents. Tick thread only, like the chunk stream. */
@Component
class OreHandOut(
  private val oreYield: OreYield,
  private val richVeins: RichVeins,
  private val lootItems: LootItemEntitySpawner,
) : CarveYield {

  override val reads: ComponentClassSet = richVeins.reads

  override val writes: ComponentClassSet = setOf(ObtainItemIntent.CreateItemIntent::class)

  /**
   * Ore a carve broke that its owner has not been handed yet.
   *
   * Only ever holds a second stack for a tick: a brush is metres wide and ore bodies are kilometres
   * apart, so one carve is one resource. It exists because the intent is a component and a second one
   * would overwrite the first, not because a queue was wanted.
   */
  private val unclaimed = HashMap<EntityId, MutableMap<Long, Int>>()

  private class Scattered(val at: Vec3L, val itemId: Long, val amount: Int)

  /** Ore a blast threw on the ground, spawned with the next hand-out. */
  private val scattered = ArrayList<Scattered>()

  /**
   * A voxel pays only once it is gone. A brush that shaves a third off an ore block has not got the ore
   * out of it, and paying per fraction would turn one deposit into as many lumps as a player cares to
   * click - which, since this is where the world's money comes from, is the difference between mining
   * and printing.
   */
  override fun bank(world: World, result: ChunkService.CarveResult, entityId: EntityId, scatters: Boolean) {
    for (voxel in result.voxels) {
      if (!voxel.exhausted) continue

      val stack = oreYield.of(voxel.priorBlock) ?: continue
      val pieces = stack.amount + richVeins.extraPieces(world, entityId, stack)

      if (scatters) {
        // Where the block was, so a blast's ore lies spread over the crater it made.
        scattered += Scattered(Vec3L(voxel.voxelX, voxel.voxelY, voxel.voxelZ.toLong()), stack.itemId, pieces)
      } else {
        val owed = unclaimed.getOrPut(entityId) { HashMap() }
        owed[stack.itemId] = (owed[stack.itemId] ?: 0) + pieces
      }
    }
  }

  /** One stack per entity per tick; `ObtainItemIntentSystem` at 59 picks them up in the same pass. */
  override fun handOut(world: World) {
    scatter(world)
    val entries = unclaimed.entries.iterator()

    while (entries.hasNext()) {
      val (entityId, owed) = entries.next()
      val next = owed.entries.firstOrNull()

      if (next == null) {
        entries.remove()
        continue
      }
      if (world.get(entityId, ObtainItemIntent.CreateItemIntent::class) != null) continue

      world.add(entityId, ObtainItemIntent.CreateItemIntent(next.key, next.value))
      owed.remove(next.key)
      if (owed.isEmpty()) entries.remove()
    }
  }

  /**
   * Nobody's loot: whoever reaches a scattered piece first picks it up. Spawned between ticks, because a new ground
   * item touches components the chunk stream has no business declaring.
   */
  private fun scatter(world: World) {
    if (scattered.isEmpty()) return

    val drops = scattered.toList()
    scattered.clear()
    world.post { drops.forEach { drop -> lootItems.spawnLootItem(this, drop.itemId, drop.amount, drop.at) } }
  }
}
