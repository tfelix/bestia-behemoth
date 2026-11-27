package net.bestia.zone.doomsday

import net.bestia.worldgen.core.WorldWrap
import net.bestia.zone.casting.sense.BrewingEvents
import net.bestia.zone.doomsday.persistence.StoredDoomsday
import net.bestia.zone.weather.DoomSkies
import net.bestia.zone.weather.DoomSky
import net.bestia.zone.world.WorldService
import net.bestia.zone.worldevent.RunningEvents
import org.springframework.stereotype.Component
import kotlin.math.roundToInt

/**
 * The doomsday as the world shows it: in each place's sky, and to the senses that feel what is brewing. Holds what
 * the director last read, because the weather asks on the tick, for every player, and must not wait for the
 * database.
 */
@Component
class DoomsdaySigns(
  private val worldService: WorldService,
  private val brewing: RunningEvents,
) : DoomSkies {

  private data class Seen(val kind: DoomKind, val x: Double, val y: Double, val stage: DoomStage, val progress: Double)

  @Volatile
  private var seen: Seen? = null

  private val wrap by lazy { WorldWrap(worldService.config) }
  private val voxelMetres by lazy { worldService.config.voxelSize }

  /** Called whenever the director reads or saves the doomsday. Nothing shows before its onset. */
  fun remember(row: StoredDoomsday) {
    val kind = row.kind
    val x = row.originX
    val y = row.originY
    val started = row.steppedToDay >= row.onsetDay
    val now = if (kind == null || x == null || y == null || !started) null else Seen(kind, x, y, row.stage, row.progress)
    seen = now
    brewing.replace(SOURCE, listOfNotNull(now?.let(::brewingOf)))
  }

  /** The doomsday's kind and stage, as far as the world shows them: null before its onset. */
  fun known(): Pair<DoomKind, DoomStage>? {
    val doom = seen ?: return null
    return doom.kind to doom.stage
  }

  override fun at(x: Long, y: Long): DoomSky {
    val doom = seen ?: return DoomSky.CLEAR
    val distance = wrap.distance(x * voxelMetres, y * voxelMetres, doom.x, doom.y)
    val presence = DoomPresence.at(distance, doom.stage, doom.progress)
    val level = (presence * DoomSky.HEART).roundToInt()

    return if (level == 0) DoomSky.CLEAR else DoomSky(doom.kind.name, level)
  }

  private fun brewingOf(doom: Seen): BrewingEvents.Brewing {
    return BrewingEvents.Brewing(
      x = doom.x,
      y = doom.y,
      family = FAMILY,
      eventId = "DOOM_${doom.kind.name}",
      feltFrom = FELT_FROM,
      stage = when (doom.stage) {
        DoomStage.OMENS -> BrewingEvents.Stage.STIRRING
        DoomStage.ONSET, DoomStage.ESCALATION -> BrewingEvents.Stage.GATHERING
        DoomStage.CATACLYSM -> BrewingEvents.Stage.BREAKING
      },
    )
  }

  private companion object {
    const val SOURCE = "doomsday"
    const val FAMILY = "doom"

    /** Ten times the reach of a world event: Observation feels the doomsday from across the world. */
    const val FELT_FROM = 10.0
  }
}
