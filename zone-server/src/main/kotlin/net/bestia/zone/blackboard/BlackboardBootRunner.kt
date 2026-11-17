package net.bestia.zone.blackboard

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.World
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Raises every quest board: the ones players built, and one beside each start town's market hall. After the towns (113). */
@Component
@Order(115)
class BlackboardBootRunner(
  private val boards: Blackboards,
  private val startTownBoards: StartTownBoards,
  private val offers: BlackboardOffers,
  private val world: World,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    val built = boards.built()
    built.forEach { boards.raise(world, it.position, it.id) }

    val startTownSpots = startTownBoards.spots()
    startTownSpots.forEach { boards.raise(world, it, structureId = null) }
    offers.refresh()

    LOG.info { "Raised ${built.size} built blackboards and ${startTownSpots.size} in the start towns" }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
