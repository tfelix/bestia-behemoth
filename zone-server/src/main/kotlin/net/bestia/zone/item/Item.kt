package net.bestia.zone.item

import jakarta.persistence.*
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.util.requireValidIdentifier

@Entity
@Table(
  name = "item",
  indexes = [
    Index(columnList = "identifier", unique = true)
  ]
)
class Item(
  @Id
  var id: Long = 0,

  val identifier: String,
  /**
   * Fixed point mass at 100 per kilogram, so one unit is 10g - fine enough that the lightest thing in
   * the catalogue, a single sheet of vellum, still weighs something instead of rounding away to nothing.
   *
   * [net.bestia.zone.ecs.item.WeightLimitCalculator] returns a carry limit on this same scale, so the
   * two are compared without conversion. The client is the only place the scale is spelled out again,
   * because it is the only place a kilogram is ever displayed.
   */
  var weight: Int,
  var type: ItemType,

  /**
   * Whether fresh grants of this item merge into a single stack. Items that carry per-instance
   * state ([net.bestia.zone.item.instance.ItemInstance] - upgrade level, forged-by-master, ...)
   * are never stacked regardless of this flag; this only decides how a plain, freshly obtained
   * item is stored. Equipment defaults to non-stackable.
   */
  var stackable: Boolean = (type != ItemType.EQUIP),

  /**
   * Name of the [net.bestia.zone.item.script.ItemScript] implementation used to execute this
   * item's usage effect. Required for [ItemType.USABLE] items.
   */
  @Column(nullable = true)
  var script: String? = null,

  /**
   * The single slot this item is worn in. Required for [ItemType.EQUIP] and always null otherwise.
   */
  @Enumerated(EnumType.STRING)
  @Column(nullable = true)
  var equipSlot: EquipmentSlot? = null,

  /**
   * The tier this item belongs to: what it takes to have anything to do with it.
   *
   * One number answering three questions, which is the whole reason it is a property of the *template* rather
   * than of a use of it. It is the level a wearer needs to put gear on
   * ([net.bestia.zone.item.equip.EquipmentService]), the reach a crafter needs to make it or work on it
   * ([net.bestia.zone.crafting.MasterCraftBonusService.maxItemLevel]), and the tier a material belongs to.
   *
   * **A material's level gates nothing on its own today**, and that is deliberate rather than an oversight:
   * a recipe already says which skill and which rank it needs, so gating its inputs as well would be the same
   * rule stated twice and drifting. What the number does for a material is say which tier of work it is *for*,
   * which is what a player reads off it and what a future recipe is priced against.
   *
   * An instance's *effective* level is this plus its upgrade level - see
   * [net.bestia.zone.item.instance.ItemInstance.upgradeLevel]. A well-upgraded sword is harder to work on than
   * a plain one of the same kind, which is what makes the two numbers interact rather than merely coexist.
   */
  var level: Int = 1,

  /**
   * How much wear a fresh [net.bestia.zone.item.instance.ItemInstance] of this template can take, or
   * **0 for an item that does not wear at all** - which is every material, every consumable and any
   * piece of gear nobody has given a number to yet.
   *
   * Only ever read when an instance is minted; from then on the instance owns its own copy, so that a
   * well-forged sword can be tougher than the catalogue's baseline. See [ItemInstance.maxDurability].
   */
  var maxDurability: Int = 0,

  /**
   * Long-form flavor text, English only.
   */
  @Column(columnDefinition = "TEXT", nullable = true)
  var description: String? = null
) {

  init {
    requireValidIdentifier(identifier)
    validate()
  }

  /**
   * What this item counts as when a crafter looks at one particular copy of it.
   *
   * Every upgrade makes an item that little bit more demanding to touch, so a `+6` sword can be beyond a smith
   * who repairs the plain one all day. That is the interaction the two numbers exist for; without it,
   * upgrade level would be a stat bonus and nothing else.
   */
  fun effectiveLevel(upgradeLevel: Int): Int = level + upgradeLevel

  /**
   * The invariants tying [type] to [script] and [equipSlot].
   *
   * Extracted from `init` rather than left inline because the importer now *updates* an existing row in
   * place, and an `init` block only ever guards the construction path - an item edited from USABLE to ETC
   * without dropping its script would sail straight past it.
   */
  fun validate() {
    if (type == ItemType.USABLE) {
      requireNotNull(script) {
        "Item $identifier is USABLE and must have a script attached"
      }
    }

    if (type == ItemType.EQUIP) {
      requireNotNull(equipSlot) {
        "Item $identifier is EQUIP and must declare which equipSlot it is worn in"
      }
    } else {
      require(equipSlot == null) {
        "Item $identifier is $type and must not declare an equipSlot"
      }
    }
  }

  enum class ItemType {
    USABLE,
    EQUIP,
    ETC
  }
}
