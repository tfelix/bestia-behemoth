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
)
