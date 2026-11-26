package net.bestia.zone.doomsday.foreshadow

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.doomsday.DoomStageNotices
import net.bestia.zone.doomsday.DoomsdayConfig
import net.bestia.zone.doomsday.ForeshadowKind
import net.bestia.zone.doomsday.persistence.StoredDoomsday
import net.bestia.zone.doomsday.persistence.StoredForeshadowing
import net.bestia.zone.doomsday.persistence.StoredForeshadowingRepository
import net.bestia.zone.place.ecs.ProvinceService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import kotlin.random.Random

/**
 * Runs the world's Foreshadowing for the director: strikes a random province outside the sanctuaries on its day,
 * lets it act until it runs out, and answers how far its outcome moves the onset.
 */
@Service
class ForeshadowRunner(
  private val config: DoomsdayConfig,
  private val seasons: StoredForeshadowingRepository,
  private val provinces: ProvinceService,
  private val notices: DoomStageNotices,
  beans: ObjectProvider<Foreshadowing>,
) {

  private val foreshadowings: Map<ForeshadowKind, Foreshadowing> by lazy {
    beans.orderedStream().toList().associateBy { it.kind }
  }

  fun built(): List<ForeshadowKind> {
    return foreshadowings.keys.sorted()
  }

  /** Runs the Foreshadowing of [fate] at world age [ageDays], and answers by how many days it moved the onset. */
  fun run(fate: StoredDoomsday, ageDays: Double, realSeconds: Double, worldSeed: Long): Double {
    val foreshadowing = fate.foreshadowKind?.let { foreshadowings[it] } ?: return 0.0
    val season = seasons.findById(StoredForeshadowing.ONLY).orElse(null)
    if (season == null) {
      if (ageDays >= fate.foreshadowDay && ageDays < fate.onsetDay) {
        begin(foreshadowing, ageDays, Random(worldSeed xor SALT))
      }
      return 0.0
    }
    if (season.contained != null) {
      return 0.0
    }

    foreshadowing.step(season, realSeconds)
    seasons.save(season)
    return if (ageDays >= season.endsDay) finish(foreshadowing, season, null) else 0.0
  }

  /** Ends the running Foreshadowing now, as [contained] says. For a GM; null when none runs. */
  fun end(contained: Boolean): Double? {
    val season = seasons.findById(StoredForeshadowing.ONLY).orElse(null)?.takeIf { it.contained == null } ?: return null
    val foreshadowing = foreshadowings[season.kind] ?: return null
    return finish(foreshadowing, season, contained)
  }

  /** Forgets the Foreshadowing, so it can strike again. For a GM. */
  fun forget() {
    seasons.deleteAll()
  }

  private fun begin(foreshadowing: Foreshadowing, ageDays: Double, random: Random) {
    val province = provinces.provinces.provinces.filterNot(provinces::isSanctuary).randomOrNull(random) ?: return
    val days = config.foreshadowMinDays + random.nextDouble() * (config.foreshadowMaxDays - config.foreshadowMinDays)
    val at = province.seat.position
    seasons.save(
      StoredForeshadowing(
        kind = foreshadowing.kind,
        provinceIndex = province.index,
        x = at.x,
        y = at.y,
        endsDay = ageDays + days,
      )
    )
    notices.foreshadowBegan(foreshadowing.kind, at.x, at.y)
    LOG.warn { "The Foreshadowing ${foreshadowing.kind} struck ${province.name} for ${days.toInt()} days" }
  }

  private fun finish(foreshadowing: Foreshadowing, season: StoredForeshadowing, forced: Boolean?): Double {
    val contained = forced ?: foreshadowing.contained(season)
    season.contained = contained
    foreshadowing.end(season, contained)
    seasons.save(season)
    notices.foreshadowEnded(season.kind, contained)
    LOG.warn { "The Foreshadowing ${season.kind} ended, ${if (contained) "contained" else "not contained"}" }

    return if (contained) config.foreshadowShiftDays else -config.foreshadowShiftDays
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    /** Keeps the province draw apart from the doomsday's own draws. */
    const val SALT = 0x0F0E_5AD0L
  }
}
