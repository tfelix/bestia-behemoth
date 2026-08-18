package net.bestia.zone.item.instance

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import net.bestia.zone.account.master.Master
import net.bestia.zone.item.Item

/**
 * A single, uniquely identifiable item that carries per-instance state (an upgrade level, the
 * master that forged/crafted it, its wear and the rune slots cut into it). An instance is
 * **owner-agnostic**: it keeps its identity while it moves between a bestia inventory, a master
 * inventory, an NPC inventory or lies on the ground, so that state is never lost in transit. An
 * instance always represents exactly one physical item (quantity 1) and never stacks.
 *
 * Plain, common items (a pile of apples) do not get an instance row - they are stored purely as a
 * template reference plus an amount on a [net.bestia.zone.item.container.ContainerSlot].
 */
@Entity
@Table(name = "item_instance")
class ItemInstance(
  @ManyToOne
  @JoinColumn(name = "item_id", nullable = false)
  val item: Item,

  @ManyToOne
  @JoinColumn(name = "crafted_by_master_id", nullable = true)
  val craftedBy: Master? = null,

  var upgradeLevel: Int = 0,

  /**
   * How much wear this item can take before it stops working, copied from [Item.maxDurability] when
   * the instance is minted rather than read through the template on every access - an item forged by
   * a good smith is meant to be able to outlast a plain one of the same kind, and re-reading the
   * template would erase that the moment the catalogue was retuned.
   *
   * **Zero means this item does not wear at all**, which is also what `ddl-auto` backfills onto every
   * row written before this column existed. That reads correctly: nothing wore out before there was
   * wear, and no repair skill should suddenly find those items broken.
   */
  var maxDurability: Int = item.maxDurability,

  /** Only meaningful while [maxDurability] > 0. Zero with a nonzero max is a broken item. */
  var durability: Int = maxDurability,

  /**
   * Rune slots cut into this item by Item Customization; zero for everything untouched.
   *
   * A count rather than a list of what sits in them, because nothing can fill one yet - runes are
   * Artificer work (`RUNIC_ETCHING`) and that sub-tree is not built. Cutting the slot is the whole of
   * what the Craftsman tree does, and the docs' table for Item Customization is exactly a slot cap.
   */
  var slots: Int = 0,
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  val wears: Boolean get() = maxDurability > 0

  /** True once wear has used the item up. Never true for an item that does not wear. */
  val isBroken: Boolean get() = wears && durability <= 0

  /** Takes [amount] off, floored at broken. No-op for an item that does not wear. */
  fun wear(amount: Int) {
    require(amount >= 0) { "amount >= 0 required, was $amount" }
    if (!wears) return

    durability = (durability - amount).coerceAtLeast(0)
  }

  /**
   * Restores [amount] of wear, capped at [maxDurability], and answers how much was actually restored -
   * which is what tells a repair whether it did anything at all.
   */
  fun repair(amount: Int): Int {
    require(amount >= 0) { "amount >= 0 required, was $amount" }
    if (!wears) return 0

    val restored = minOf(amount, maxDurability - durability)
    durability += restored

    return restored
  }
}
