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
 * master that forged/crafted it, later durability/sockets/binding). An instance is
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
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}
