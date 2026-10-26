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
) {

  companion object {
    /**
     * The order a craft, a sale or any other "some of this item" takes piles in: plain piles first, then by
     * maker. The database and the live inventory both follow it, so both take the same units.
     */
    val TAKE_ORDER: Comparator<ItemProvenance?> =
      nullsFirst(compareBy<ItemProvenance> { it.crafterName }.thenBy { it.worldName })
  }
}
