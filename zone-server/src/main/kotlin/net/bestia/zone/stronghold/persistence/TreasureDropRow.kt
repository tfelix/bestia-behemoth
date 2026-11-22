package net.bestia.zone.stronghold.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.time.Instant

/** A waiting treasure drop, kept with its Stronghold row and gone with it. */
@Embeddable
class TreasureDropRow(
  @Column(name = "made_at", nullable = false)
  var madeAt: Instant = Instant.EPOCH,

  @Column(nullable = false)
  var coins: Long = 0,

  /** `itemId:amount` pairs, comma separated. A drop holds a handful of kinds, so a table of its own buys nothing. */
  @Column(nullable = false, length = 512)
  var goods: String = "",
)
