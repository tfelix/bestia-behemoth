package net.bestia.zone.doomsday.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** A corruption crystal of the Withering that still stands, at [x], [y] in metres. Broken ones are deleted. */
@Entity
@Table(name = "corruption_crystal")
class StoredCorruptionCrystal(
  @Column(nullable = false)
  val x: Double,

  @Column(nullable = false)
  val y: Double,

  /** Grown by a Blight Bloom rather than the Withering's front. */
  @Column(nullable = false)
  val bloom: Boolean = false,
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}
