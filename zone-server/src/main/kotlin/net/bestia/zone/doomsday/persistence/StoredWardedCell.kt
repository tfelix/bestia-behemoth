package net.bestia.zone.doomsday.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** A mana-field cell a cleansing ritual warded against the Withering, until a real epoch day. */
@Entity
@Table(name = "warded_cell")
class StoredWardedCell(
  /** `ManaField.Cell.key`. */
  @Id
  @Column(name = "cell_key", nullable = false)
  var cellKey: Long = 0,

  @Column(name = "until_day", nullable = false)
  var untilDay: Double = 0.0,
)
