package net.bestia.zone.doomsday.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import net.bestia.zone.doomsday.DoomEnding
import java.time.Instant

/** The open Last Stand or Calling, stored so a restart keeps its countdown. There is at most one row. */
@Entity
@Table(name = "world_end")
class StoredWorldEnd(
  @Id
  var id: Long = ONLY,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  var ending: DoomEnding,

  @Column(name = "ends_at", nullable = false)
  var endsAt: Instant,

  /** The countdown mark announced last, in minutes, so a restart does not announce it again. */
  @Column(name = "warned_at_minutes")
  var warnedAtMinutes: Int? = null,
) {

  companion object {
    const val ONLY = 1L
  }
}
