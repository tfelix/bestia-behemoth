package net.bestia.zone.spellscroll.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import net.bestia.zone.item.persistence.ItemInstance

/**
 * The attack one spell scroll holds. Keyed by the scroll's item instance, like `map_chart`, so a scroll that is
 * traded, dropped or looted keeps its attack with no code in those paths knowing about scrolls.
 */
@Entity
@Table(
  name = "spell_scroll",
  indexes = [Index(name = "idx_spell_scroll_item_instance", columnList = "item_instance_id", unique = true)]
)
class SpellScrollInscription(
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "item_instance_id", nullable = false, unique = true)
  val itemInstance: ItemInstance,

  @Column(name = "skill_id", nullable = false)
  val skillId: Long,

  @Column(name = "level", nullable = false)
  val level: Int,
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0
}
