package net.bestia.zone.doomsday.foreshadow

import net.bestia.zone.doomsday.ForeshadowKind
import net.bestia.zone.doomsday.persistence.StoredForeshadowing
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.prop.omen.OmenRaiser
import net.bestia.zone.world.mana.ManaField
import net.bestia.zone.world.mana.WoundFootprint
import net.bestia.zone.worldevent.persistence.StoredRift.Phase
import net.bestia.zone.worldevent.rift.Rifts
import org.springframework.stereotype.Component
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A Foreshadowing that floods one province with mana, so rifts and storms rise there by themselves. Each rift in the
 * province that players seal or that dissolves counts as held, each one that collapses as lost. Left to run its
 * course, it scars the land around its heart.
 */
@Component
class ManaStormSeason(
  private val config: ManaStormSeasonConfig,
  private val field: ManaField,
  private val rifts: Rifts,
  private val world: WorldView,
  private val omens: OmenRaiser,
) : Foreshadowing {

  override val kind = ForeshadowKind.MANA_STORM_SEASON

  /** The province's rifts seen while it runs. A restart forgets the ones that end before they are seen again. */
  private val watched = HashSet<Long>()

  private var footprint: WoundFootprint? = null

  override fun step(season: StoredForeshadowing, realSeconds: Double) {
    val footprint = footprint ?: WoundFootprint(season.x, season.y, config.reachMetres, field)
    this.footprint = footprint
    footprint.pour(field, config.motesPerDay * realSeconds / SECONDS_PER_DAY)

    rifts.active().filter { it.provinceIndex == season.provinceIndex }.forEach { watched += it.id }
    val ended = watched.filter { rifts.phaseOf(it)?.isActive != true }
    for (id in ended) {
      when (rifts.phaseOf(id)) {
        Phase.SEALED, Phase.DISSOLVED -> season.held++
        Phase.COLLAPSED -> season.lost++
        else -> {}
      }
      watched.remove(id)
    }
  }

  override fun end(season: StoredForeshadowing, contained: Boolean) {
    footprint = null
    watched.clear()
    if (contained) {
      return
    }

    world.post {
      repeat(config.scars) { i ->
        val angle = 2 * PI * i / config.scars
        val x = season.x + cos(angle) * config.scarRadiusMetres
        val y = season.y + sin(angle) * config.scarRadiusMetres
        omens.raiseAt(this, x, y)
      }
    }
  }

  private companion object {
    const val SECONDS_PER_DAY = 86_400.0
  }
}
