package net.bestia.zone.doomsday.withering

import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.doomsday.persistence.StoredCorruptionCrystal
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.ecs.EventCreature
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.util.EntityId
import net.bestia.zone.worldevent.EventGround
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Keeps a crystal standing in the world for every stored one, and notices which were broken. The entities are not
 * stored: after a restart they grow again from the rows.
 */
@Component
class CorruptionCrystals(
  private val world: WorldView,
  private val catalogue: BestiaCatalogue,
  private val spawner: BestiaEntitySpawner,
  private val ground: EventGround,
) {

  private val standing = ConcurrentHashMap<Long, EntityId>()
  private val broken = ConcurrentLinkedQueue<Long>()

  private val speciesId: Long by lazy { catalogue.byIdentifier(SPECIES).id }

  /** Spawns the crystals of [rows] that have no entity, and removes the entities of rows that are gone. */
  fun keep(rows: List<StoredCorruptionCrystal>, level: Int) {
    val ids = rows.map { it.id }.toSet()
    world.post {
      standing.entries.removeIf { (rowId, entity) ->
        when {
          rowId !in ids -> {
            if (isAlive(entity)) destroy(entity)
            true
          }
          !isAlive(entity) -> {
            broken += rowId
            true
          }
          else -> false
        }
      }

      rows.filterNot { standing.containsKey(it.id) }.forEach { row ->
        val at = ground.grounded(row.x, row.y) ?: return@forEach
        val crystal = spawner.spawnMob(this, speciesId, at, persistent = false, homePosition = at, level = level)
        add(crystal, EventCreature(EVENT, row.id))
        standing[row.id] = crystal
      }
    }
  }

  /** The rows whose crystal was broken since the last ask. */
  fun takeBroken(): List<Long> {
    return generateSequence { broken.poll() }.toList()
  }

  private companion object {
    const val EVENT = "Withering"
    const val SPECIES = "corruption_crystal"
  }
}
