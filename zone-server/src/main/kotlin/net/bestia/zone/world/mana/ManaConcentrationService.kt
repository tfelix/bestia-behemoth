package net.bestia.zone.world.mana

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service

/** The mana field of the generated world where something stands. */
@Service
class ManaConcentrationService(
  private val worldService: WorldService,
) {

  /** The field at [at], 0 on a world without the mana stage. */
  fun manaAt(at: Vec3L): Double {
    return worldService.generated.materializer.surface.manaAt(at.x.toDouble(), at.y.toDouble())
  }

  fun placeAt(at: Vec3L): Int = ManaConcentration.place(manaAt(at))

  fun factorAt(at: Vec3L): Double = ManaConcentration.factor(manaAt(at))
}
