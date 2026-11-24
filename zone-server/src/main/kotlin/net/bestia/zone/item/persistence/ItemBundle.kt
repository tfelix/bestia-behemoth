package net.bestia.zone.item.persistence

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table

/**
 * The items one bundle holds. Keyed by the bundle's item instance, like `spell_scroll`, so a bundle that is
 * dropped, looted or traded keeps its contents with no code in those paths knowing about bundles.
 */
@Entity
@Table(
  name = "item_bundle",
  indexes = [Index(name = "idx_item_bundle_item_instance", columnList = "item_instance_id", unique = true)]
)
class ItemBundle(
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "item_instance_id", nullable = false, unique = true)
  val itemInstance: ItemInstance,
) {

  @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true, optional = false)
  @JoinColumn(name = "container_id", nullable = false)
  val container: ItemContainer = ItemContainer(ItemContainer.Type.BUNDLE)

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}
