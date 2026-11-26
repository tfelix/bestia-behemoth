package net.bestia.zone.doomsday.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import net.bestia.zone.doomsday.ForeshadowKind

/** The Foreshadowing of the current world, once it struck. There is only ever one, and only one row. */
@Entity
@Table(name = "foreshadowing")
class StoredForeshadowing(
  @Id
  var id: Long = ONLY,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  var kind: ForeshadowKind,

  @Column(name = "province_index", nullable = false)
  var provinceIndex: Int,

  /** Where in its province it strikes hardest, in metres. */
  @Column(nullable = false)
  var x: Double,

  @Column(nullable = false)
  var y: Double,

  /** The day of world age it runs out. */
  @Column(name = "ends_day", nullable = false)
  var endsDay: Double,

  /** What players held of it, and what they lost to it, in whatever it counts. */
  @Column(nullable = false)
  var held: Int = 0,

  @Column(nullable = false)
  var lost: Int = 0,

  /** Null while it runs. */
  @Column
  var contained: Boolean? = null,
) {

  companion object {
    const val ONLY = 1L
  }
}
