package net.bestia.zone.item.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.bestia.zone.item.enchant.ActiveItemEnchantment
import net.bestia.zone.item.enchant.ItemEnchantmentId
import java.time.Instant

/**
 * One enchantment on an [ItemInstance]. [endsAt] is a wall-clock time, not a countdown, because nothing ticks an
 * item in a bag: an ended row is ignored on read, and [net.bestia.zone.item.enchant.ItemEnchantmentSweep] deletes it.
 */
@Entity
@Table(
  name = "item_instance_enchantment",
  uniqueConstraints = [UniqueConstraint(columnNames = ["item_instance_id", "enchantment_id"])],
  indexes = [Index(columnList = "ends_at")]
)
class ItemInstanceEnchantment(
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "item_instance_id")
  val itemInstance: ItemInstance,

  @Column(name = "enchantment_id", nullable = false)
  val enchantmentId: Long,

  strength: Int,
  value: Int,
  endsAt: Instant?,
) {

  constructor(itemInstance: ItemInstance, enchantment: ActiveItemEnchantment) : this(
    itemInstance, enchantment.enchantment.id, enchantment.strength, enchantment.value, enchantment.endsAt
  )

  @Column(nullable = false)
  var strength: Int = strength

  // `value` is a reserved word in H2.
  @Column(name = "enchantment_value", nullable = false)
  var value: Int = value

  @Column(name = "ends_at")
  var endsAt: Instant? = endsAt

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  /** In place: a delete and an insert of the same kind would meet the unique key before the delete is flushed. */
  fun renew(enchantment: ActiveItemEnchantment) {
    strength = enchantment.strength
    value = enchantment.value
    endsAt = enchantment.endsAt
  }

  /** Null for a row whose enchantment was removed from the catalog. */
  fun toActive(): ActiveItemEnchantment? {
    val enchantment = ItemEnchantmentId.findById(enchantmentId) ?: return null

    return ActiveItemEnchantment(enchantment, strength, value, endsAt)
  }
}
