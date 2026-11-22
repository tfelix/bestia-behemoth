package net.bestia.zone.stronghold

import net.bestia.zone.cartography.survey.SurveyWitness
import net.bestia.zone.ecs.core.World
import org.springframework.stereotype.Component
import kotlin.math.hypot

/** A Cartography survey that charts an awake seed discovers it for the surveyor. */
@Component
class SeedSurveys(
  private val seeds: EmpyriteSeedRegistry,
  private val discoveries: EmpyriteSeedDiscoveries,
) : SurveyWitness {

  override fun surveyed(world: World, ownerMasterId: Long, centreX: Double, centreY: Double, radiusMetres: Double) {
    seeds.awake()
      .filter { hypot(it.x - centreX, it.y - centreY) <= radiusMetres }
      .forEach { discoveries.discover(ownerMasterId, it.rank) }
  }
}
