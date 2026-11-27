package net.bestia.zone.doomsday.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** A mana-field cell whose plants the Withering killed, so they are not looked for again. */
@Entity
@Table(name = "withered_cell")
class StoredWitheredCell(
  /** `ManaField.Cell.key`. */
  @Id
  @Column(name = "cell_key", nullable = false)
  var cellKey: Long = 0,
)
