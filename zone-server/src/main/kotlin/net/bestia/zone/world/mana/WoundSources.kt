package net.bestia.zone.world.mana

import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

/** The Wounds of the world, pouring mana into the land around them. They grow stronger as the world ages. */
@Component
class WoundSources(
  private val worldService: WorldService,
  private val field: ManaField,
  private val config: ManaEmissionConfig,
  private val clock: Clock = Clock.systemUTC(),
) {

  private val footprints: List<WoundFootprint> by lazy {
    worldService.generated.world.features.all()
      .filter { it.kind == FeatureKind.WOUND }
      .filterIsInstance<PointMarker>()
      .map { WoundFootprint(it.position.x, it.position.y, config.woundReachMetres, field) }
  }

  fun pour(seconds: Double) {
    if (footprints.isEmpty()) return

    val motes = motesPerSecond(config, worldAgeDays()) * seconds
    footprints.forEach { it.pour(field, motes) }
  }

  private fun worldAgeDays(): Double {
    val age = Duration.between(worldService.record.createdAt, clock.instant())
    return age.toSeconds() / SECONDS_PER_DAY
  }

  companion object {
    private const val SECONDS_PER_DAY = 86_400.0

    /** What one Wound pours out at a world age of [ageDays]: a trickle at first, full strength once old. */
    fun motesPerSecond(config: ManaEmissionConfig, ageDays: Double): Double {
      val grown = (ageDays / config.woundFullAgeDays).coerceIn(0.0, 1.0)
      val share = config.woundYoungShare + (1.0 - config.woundYoungShare) * grown
      return config.woundMotesPerDay * share / SECONDS_PER_DAY
    }
  }
}
