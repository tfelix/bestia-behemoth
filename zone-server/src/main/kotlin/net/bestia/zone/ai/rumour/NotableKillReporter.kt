package net.bestia.zone.ai.rumour

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.bestia.BestiaRepository
import org.springframework.stereotype.Service

/**
 * Decides whether something that just died was worth talking about, and tells the towns if it was.
 *
 * Separate from the death path on purpose. What counts as notable is a judgement about *news* rather
 * than about dying, and putting it here keeps `DeathSystem` to the two lines that know a thing has
 * died - it already carries enough.
 *
 * The threshold is the whole of the design here. Without one every rat killed outside a village is
 * news, the ledger fills with things nobody would mention, and the one memory a player was meant to
 * hunt for is buried under vermin.
 */
@Service
class NotableKillReporter(
  private val rumours: RumourService,
  private val bestiaRepository: BestiaRepository,
) {

  /**
   * Posts news of a kill if the species was large enough to be worth it.
   *
   * Takes the species id and a position rather than an entity: by the time this runs the entity is
   * being destroyed, and a caller that handed over a live reference would be racing its own tick.
   */
  fun report(speciesId: Long, voxelX: Long, voxelY: Long) {
    val species = bestiaRepository.findById(speciesId).orElse(null) ?: return

    if (species.level < NOTABLE_LEVEL) {
      return
    }

    val heard = rumours.post(
      kind = RumourKind.BOSS_SLAIN,
      voxelX = voxelX,
      voxelY = voxelY,
      strength = strengthOf(species.level),
    )

    LOG.debug { "Kill of ${species.identifier} (level ${species.level}) reached ${heard.size} settlement(s)" }
  }

  /**
   * How big a deal the kill was, from the species' level.
   *
   * Ramped from the threshold rather than from zero, so the smallest thing worth mentioning is worth
   * barely anything - one village, one household - and only something near the ceiling is heard across
   * a province. A linear map from level 0 would have made every qualifying kill a regional event.
   */
  private fun strengthOf(level: Int): Double {
    val above = (level - NOTABLE_LEVEL).toDouble()

    return (MIN_STRENGTH + above / (CEILING_LEVEL - NOTABLE_LEVEL)).coerceIn(MIN_STRENGTH, 1.0)
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    /** Below this a kill is somebody's afternoon rather than the town's news. */
    const val NOTABLE_LEVEL = 20

    /** The level at which a kill is heard as far as anything ever is. */
    const val CEILING_LEVEL = 80

    const val MIN_STRENGTH = 0.15
  }
}
