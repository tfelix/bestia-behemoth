package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript

/**
 * What a Bard's song does to one who hears it, handed out by its [SongScript]. Level is the song's level, amount the
 * Bard's [Songcraft.effectPercent].
 */
abstract class HeardSongScript : StatusEffectScript {

  /** The song hands it out anew every second; a stored copy could only be stale. */
  override val isPersisted: Boolean = false

  /** Its seconds are only how long it outlasts the field. */
  override val showsCountdown: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return AuraScript.DEFAULT_LINGER_SECONDS
  }

  protected fun songLevel(level: Int): Int {
    return level.coerceIn(1, MAX_SONG_LEVEL)
  }

  private companion object {
    const val MAX_SONG_LEVEL = 5
  }
}
