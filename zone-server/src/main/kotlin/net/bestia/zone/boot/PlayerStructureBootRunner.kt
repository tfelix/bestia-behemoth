package net.bestia.zone.boot

import net.bestia.zone.world.prop.PlayerStructureRegistry
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Loads what players have built, so [net.bestia.zone.world.prop.PlayerStructureSource] can answer per chunk
 * column from memory rather than querying the table from the tick thread.
 *
 * Grouped with [WorldObjectDivergenceBootRunner] (`@Order(3)`) under "things about the world" - both exist so
 * that materialising a column asks nothing of the database. Unlike that one this needs nothing off
 * [net.bestia.zone.world.WorldService]: a structure is named by its own row, not by a lattice cell, so there is
 * no version to check it against.
 */
@Component
@Order(4)
class PlayerStructureBootRunner(
  private val registry: PlayerStructureRegistry,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    registry.loadAll()
  }
}
