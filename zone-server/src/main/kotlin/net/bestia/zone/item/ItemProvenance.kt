package net.bestia.zone.item

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * Who crafted an item, and in which world.
 *
 * Names rather than ids, copied at craft time: the item outlives a deleted master and a reset world, and still
 * reads "crafted by Alice in Genesis". Looted, bought and starter items have none.
 */
@Embeddable
data class ItemProvenance(
  @Column(name = "crafted_by")
  val crafterName: String,

  @Column(name = "crafted_in")
  val worldName: String,
)
