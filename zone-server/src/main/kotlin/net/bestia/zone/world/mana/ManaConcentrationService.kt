package net.bestia.zone.world.mana

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service

/** The mana where something stands. */
@Service
class ManaConcentrationService(
  private val worldService: WorldService,
) {

  /** Where the rock holds mana: the worldgen rank, 0 on a world without the mana stage. For geology, like herbs. */
  fun potentialAt(at: Vec3L): Double {
    return worldService.generated.materializer.surface.potentialAt(at.x.toDouble(), at.y.toDouble())
  }

  /** How much mana is in the air: what magic, weather and omens react to. */
  fun densityAt(at: Vec3L): Double = StartDensity.of(potentialAt(at))

  fun placeAt(at: Vec3L): Int = ManaConcentration.place(densityAt(at))

  fun factorAt(at: Vec3L): Double = ManaConcentration.factor(densityAt(at))
}
