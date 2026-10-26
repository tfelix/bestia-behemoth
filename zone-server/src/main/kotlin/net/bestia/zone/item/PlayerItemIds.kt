package net.bestia.zone.item

import net.bestia.zone.ecs.core.EntityIdGenerator
import net.bestia.zone.ecs.core.SnowflakeEntityIdGenerator

/**
 * Mints the ids a player names a held item by: the id of an
 * [net.bestia.zone.item.persistence.ItemInstance], or the handle of a plain pile in a live inventory.
 *
 * The zone mints them rather than the database, so the live inventory knows a new copy's id in the tick it is
 * granted, while the row is written a job later. They come from the zone's entity id generator, because a second
 * snowflake with the same node id would repeat its ids. A static holder rather than a bean, because an
 * [net.bestia.zone.item.persistence.ItemInstance] takes its id in its constructor.
 */
object PlayerItemIds {

  /** Replaced at boot by the zone's own generator. The default only serves tests that start no Spring context. */
  @Volatile
  private var generator: EntityIdGenerator = SnowflakeEntityIdGenerator()

  fun shareWith(generator: EntityIdGenerator) {
    this.generator = generator
  }

  fun next(): Long = generator.nextId()
}
