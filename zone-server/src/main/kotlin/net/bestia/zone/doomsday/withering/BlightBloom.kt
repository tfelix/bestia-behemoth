package net.bestia.zone.doomsday.withering

import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.doomsday.ForeshadowKind
import net.bestia.zone.doomsday.foreshadow.Foreshadowing
import net.bestia.zone.doomsday.persistence.StoredCorruptionCrystal
import net.bestia.zone.doomsday.persistence.StoredCorruptionCrystalRepository
import net.bestia.zone.doomsday.persistence.StoredForeshadowing
import net.bestia.zone.place.ecs.ProvinceService
import net.bestia.zone.worldevent.EventGround
import org.springframework.stereotype.Component
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A Foreshadowing that makes one province wither for a while: a few corruption crystals grow around its heart, the land
 * rots under them, and its plants die. Every crystal broken counts as held, every one still standing as lost.
 * Contained, the land heals; left to run its course, the rot stays as a scar.
 */
@Component
class BlightBloom(
  private val config: WitheringConfig,
  private val field: CorruptionField,
  private val rows: StoredCorruptionCrystalRepository,
  private val crystals: CorruptionCrystals,
  private val spread: CorruptionSpread,
  private val wards: WitheringWards,
  private val blight: WitheringBlight,
  private val provinces: ProvinceService,
  private val ground: EventGround,
) : Foreshadowing {

  override val kind = ForeshadowKind.BLIGHT_BLOOM

  override fun step(season: StoredForeshadowing, realSeconds: Double) {
    val broken = crystals.takeBroken()
    rows.deleteAllById(broken)
    season.held += broken.size

    if (season.held == 0 && rows.findAll().none { it.bloom }) {
      plant(season)
    }
    val blooming = rows.findAll().filter { it.bloom }
    season.lost = blooming.size

    spread.corrupt(blooming.map { Vec2d(it.x, it.y) }, realSeconds / SECONDS_PER_DAY, wards.wardedNow(), heal = false)
    blight.killPlants()
    crystals.keep(rows.findAll(), config.crystalLevelAtOnset)
  }

  override fun end(season: StoredForeshadowing, contained: Boolean) {
    rows.deleteAll(rows.findAll().filter { it.bloom })
    crystals.keep(rows.findAll(), config.crystalLevelAtOnset)
    if (contained) {
      CorruptionSpread.cellsWithin(season.x, season.y, config.bloomReachMetres + config.crystalReachMetres)
        .forEach { field.change(it, -1.0) }
      field.dropHealed()
    }
  }

  private fun plant(season: StoredForeshadowing) {
    repeat(config.bloomCrystals) { i ->
      val angle = 2 * PI * i / config.bloomCrystals
      val x = season.x + cos(angle) * config.bloomReachMetres
      val y = season.y + sin(angle) * config.bloomReachMetres
      if (!provinces.isSanctuaryAt(x, y) && ground.grounded(x, y) != null) {
        rows.save(StoredCorruptionCrystal(x, y, bloom = true))
      }
    }
  }

  private companion object {
    const val SECONDS_PER_DAY = 86_400.0
  }
}
