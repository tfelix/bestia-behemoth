package net.bestia.zone.worldevent.seal

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.worldevent.persistence.StoredSealStone

/** A seal that can wear thin, by the id its event is about and where it stands. */
sealed interface OldSeal {
  val subjectId: Long
  val at: Vec3L

  /** A crystal of the history that holds a world boss asleep. */
  data class Crystal(val propId: Long, val bossId: Long, override val at: Vec3L) : OldSeal {
    override val subjectId: Long get() = propId
  }

  /** The stone a sealed rift left, holding the mana the rift gathered. */
  data class Stone(val stone: StoredSealStone, override val at: Vec3L) : OldSeal {
    override val subjectId: Long get() = stone.id
  }
}
