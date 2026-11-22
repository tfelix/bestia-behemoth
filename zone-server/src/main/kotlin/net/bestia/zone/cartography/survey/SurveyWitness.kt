package net.bestia.zone.cartography.survey

import net.bestia.zone.ecs.core.World

/** Told on the tick when a survey's chart is collected: [ownerMasterId] charted a disc in map metres. */
fun interface SurveyWitness {
  fun surveyed(world: World, ownerMasterId: Long, centreX: Double, centreY: Double, radiusMetres: Double)
}
