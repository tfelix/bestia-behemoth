package net.bestia.zone.boot

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.spawn.Spawner
import net.bestia.zone.ecs.spawn.SpawnerCellIndex
import net.bestia.zone.ecs.spawn.WildSpawnerService
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Places every wild den the generator produced into the ECS world.
 *
 * Ordered after the mob importer (`@Order(101)`) because [WildSpawnerService] joins each den against the
 * bestia catalogue, and after the world loader for the obvious reason. Before
 * [EntityLoaderBootRunner] (`@Order(110)`) so a den is never mistaken for a persisted entity.
 *
 * ### These are components, not entities anybody saved
 *
 * A den is a pure function of the world seed, so it is recreated at every boot rather than persisted - the
 * argument is in [WildSpawnerService]. What that means here is that this runner must be **idempotent against
 * a restart and not against a re-run**: it adds one `Spawner` per marker and nothing removes them, so calling
 * it twice would double the wilderness. `CommandLineRunner` runs once per boot, which is the contract this
 * relies on.
 *
 * Nothing is spawned yet. Every den starts dormant and `SpawnerSystem` wakes the ones a player walks near, so
 * a world of thirty thousand dens costs thirty thousand components at boot and no entities at all.
 */
@Component
@Order(105)
class WildSpawnerBootRunner(
  private val wildSpawnerService: WildSpawnerService,
  private val cellIndex: SpawnerCellIndex,
  private val world: World,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    val dens = wildSpawnerService.dens
    if (dens.isEmpty()) {
      LOG.warn { "No wild dens placed - the wilderness will be empty" }
      return
    }

    for (den in dens) {
      val id = world.createEntity { id ->
        add(
          id,
          Spawner(
            identity = den.identity,
            bestiaId = den.bestiaId,
            maxSpawnCount = den.pack,
            position = den.position,
            range = den.range,
            activationRange = den.activationRange
          )
        )
      }

      // The entire write path of the index: dens never move and nothing destroys one, so this runs once per
      // den at boot and the index is read-only for the rest of the process.
      cellIndex.add(id, den.position)
    }

    // Both counts, because a den that exists but was not indexed is invisible to `SpawnerSystem` and would
    // show up as an unexplained patch of empty wilderness rather than as an error.
    LOG.info { "Placed ${dens.size} dormant wild den(s), ${cellIndex.size} indexed" }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
