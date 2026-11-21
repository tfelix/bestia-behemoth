package net.bestia.zone.stronghold

import net.bestia.zone.stronghold.persistence.StrongholdRecord
import java.time.Instant

/** In-memory mirror of one [StrongholdRecord] row. */
data class Stronghold(
  val rank: Int,
  val guildId: Long,
  val state: StrongholdRecord.State,
  val siteStructureId: Long?,
  val siteWorkedAt: Instant,
  val plantedAt: Instant,
  val empyriteHp: Int = 0,
  val tracks: Map<StrongholdTrack, TrackProgress> = emptyMap(),
) {

  fun progressOf(track: StrongholdTrack): TrackProgress {
    return tracks[track] ?: TrackProgress()
  }

  fun withProgress(track: StrongholdTrack, progress: TrackProgress): Stronghold {
    return copy(tracks = tracks + (track to progress))
  }
}
