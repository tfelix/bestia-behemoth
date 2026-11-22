package net.bestia.zone.prop

import net.bestia.zone.ecs.core.EntityIdGenerator
import net.bestia.zone.ecs.core.SnowflakeEntityIdGenerator

/**
 * Mints the ids of player structures. The zone mints them rather than the database, so a site has its id in the tick
 * it goes up while its row is written behind, and a zone of a cluster never mints another's. They come from the
 * zone's entity id generator for [net.bestia.zone.item.PlayerItemIds]' reason: a second snowflake with the same node
 * id would repeat its ids. A static holder, because a [net.bestia.zone.prop.persistence.PlayerStructure] takes its id
 * in its constructor.
 */
object PlayerStructureIds {

  /** Replaced at boot by the zone's own generator. The default only serves tests that start no Spring context. */
  @Volatile
  private var generator: EntityIdGenerator = SnowflakeEntityIdGenerator()

  fun shareWith(generator: EntityIdGenerator) {
    this.generator = generator
  }

  fun next(): Long = generator.nextId()
}
