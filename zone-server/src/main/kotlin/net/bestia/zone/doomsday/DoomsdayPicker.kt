package net.bestia.zone.doomsday

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.doomsday.persistence.StoredDoomsday
import kotlin.random.Random

/**
 * Decides a world's fate once, when the world is new: the threat, its origin, the onset, how long it unfolds and
 * the Foreshadowing. Seeded by the world, so the same world always gets the same fate.
 */
class DoomsdayPicker(
  private val config: DoomsdayConfig,
  private val threats: List<DoomThreat>,
  /** The Foreshadowings that are built. */
  private val foreshadows: List<ForeshadowKind> = emptyList(),
) {

  fun pick(worldSeed: Long): StoredDoomsday {
    val random = Random(worldSeed xor SALT)
    val threat = threatFor(random)
    val origin = threat?.let { originOf(it, random) }
    val onsetDay = config.onsetMinDays + random.nextDouble() * config.onsetSpreadDays
    val unfoldDays = config.unfoldMinDays + random.nextDouble() * (config.unfoldMaxDays - config.unfoldMinDays)
    val foreshadowShare = config.foreshadowAtMin + random.nextDouble() * (config.foreshadowAtMax - config.foreshadowAtMin)

    return StoredDoomsday(
      kind = threat?.kind,
      originX = origin?.x,
      originY = origin?.y,
      onsetDay = onsetDay,
      unfoldDays = unfoldDays,
      foreshadowKind = foreshadowFor(random),
      foreshadowDay = onsetDay * foreshadowShare,
    )
  }

  /** Every threat that fits this world, in a stable order, so a seed always draws the same one. */
  fun fitting(): List<DoomThreat> {
    return threats.filter { it.fits() }.sortedBy { it.kind }
  }

  private fun threatFor(random: Random): DoomThreat? {
    val fitting = fitting()
    val forced = config.forceKind?.let { kind -> fitting.firstOrNull { it.kind == kind } }
    if (config.forceKind != null && forced == null) {
      LOG.warn { "Forced doomsday ${config.forceKind} does not fit this world or is not built; drawing one instead" }
    }

    return forced ?: fitting.randomOrNull(random)
  }

  private fun originOf(threat: DoomThreat, random: Random): Vec2d {
    val forcedX = config.forceOriginX
    val forcedY = config.forceOriginY
    if (forcedX != null && forcedY != null) {
      return Vec2d(forcedX, forcedY)
    }

    return threat.origin(random)
  }

  private fun foreshadowFor(random: Random): ForeshadowKind? {
    val built = foreshadows.sorted()
    val forced = config.forceForeshadow?.takeIf { it in built }
    if (config.forceForeshadow != null && forced == null) {
      LOG.warn { "Forced Foreshadowing ${config.forceForeshadow} is not built; drawing one instead" }
    }

    return forced ?: built.randomOrNull(random)
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    /** Keeps the doomsday's draws apart from every other use of the world seed. */
    const val SALT = 0x0D00_D5DAL
  }
}
