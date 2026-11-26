package net.bestia.zone.doomsday.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** The Void Tear of the current world: the rift that is the Tear, and what the players did to it. One row at most. */
@Entity
@Table(name = "void_tear")
class StoredVoidTear(
  @Id
  var id: Long = ONLY,

  /** The rift that is the Tear right now; null before onset and while it is sealed. */
  @Column(name = "rift_id")
  var riftId: Long? = null,

  /** While sealed: the real epoch day it tears open again. */
  @Column(name = "reforms_at_day")
  var reformsAtDay: Double? = null,

  /** Real epoch days on which a rift within its reach was sealed, for its containment. Comma-separated. */
  @Column(name = "recent_seals", nullable = false, length = 2048)
  var recentSeals: String = "",
) {

  companion object {
    const val ONLY = 1L
  }
}
