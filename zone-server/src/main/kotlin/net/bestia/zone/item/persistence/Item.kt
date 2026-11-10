package net.bestia.zone.item.persistence

import net.bestia.zone.battle.gear.ToolKind
import net.bestia.zone.battle.gear.WeaponType
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ElementModifier
import net.bestia.zone.battle.ammo.AmmoType
import net.bestia.zone.battle.gear.ArmorType
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.material.ItemMaterial
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
   * [net.bestia.zone.item.ecs.WeightLimitCalculator] returns a carry limit on this same scale, so the
   * two are compared without conversion. The client is the only place the scale is spelled out again,
   * because it is the only place a kilogram is ever displayed.
   */
  var weight: Int,
  var type: ItemType,

  /**
   * Whether fresh grants of this item merge into a single stack. Items that carry per-instance
   * state ([net.bestia.zone.item.persistence.ItemInstance] - upgrade level, forged-by-master, ...)
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
   * The tier this item belongs to before any stat on it is counted: what it takes to have anything to do with it.
   *
   * It is where every copy's item level starts ([net.bestia.zone.item.stat.ItemLevel]), which is what a wearer
   * needs to put gear on. It is also the reach a crafter needs to make the item
   * ([net.bestia.zone.crafting.CraftBonusService.maxItemLevel]), and the tier a material belongs to.
   *
   * **A material's level gates nothing on its own today**, and that is deliberate rather than an oversight:
   * a recipe already says which skill and which rank it needs, so gating its inputs as well would be the same
   * rule stated twice and drifting. What the number does for a material is say which tier of work it is *for*,
   * which is what a player reads off it and what a future recipe is priced against.
   *
   * An instance's *effective* level is this plus its upgrade level - see
   * [net.bestia.zone.item.persistence.ItemInstance.upgradeLevel]. A well-upgraded sword is harder to work on than
   * a plain one of the same kind, which is what makes the two numbers interact rather than merely coexist.
   */
  @Column(name = "level")
  var baseLevel: Int = 1,

  /**
   * How much wear a fresh [net.bestia.zone.item.persistence.ItemInstance] of this template can take, or
   * **0 for an item that does not wear at all** - which is every material, every consumable and any
   * piece of gear nobody has given a number to yet.
   *
   * Only ever read when an instance is minted; from then on the instance owns its own copy, so that a
   * well-forged sword can be tougher than the catalogue's baseline. See [ItemInstance.maxDurability].
   */
  var maxDurability: Int = 0,

  /**
   * True for gear only a novice may wear, or a consumable only a novice may use - a novice being a master that
   * has invested no skill point outside the Novice tree. A player bestia never is one.
   *
   * Gear is checked on the way on by [net.bestia.zone.item.equip.EquipmentService.checkStillWearable] and taken
   * back off by `EquipmentRevalidationService` once the wearer stops qualifying; a consumable is checked by
   * `UseItemHandler` and simply stays in the bag. Either way the starter kit is something to outgrow rather
   * than a permanent floor under every build.
   */
  @Column(name = "novice_only", nullable = false)
  var noviceOnly: Boolean = false,

  /** Never leaves its holder's bag by hand: not dropped, traded, sold or sent. A mail sack is one. */
  @Column(nullable = false)
  var bound: Boolean = false,

  /**
   * How heavy this armor is, checked against the species of a bestia that puts it on. Null for gear that is
   * not armor, such as weapons and accessories: anyone with the slot may wear it.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "armor_type", nullable = true, length = 16)
  var armorType: ArmorType? = null,

  /**
   * What the item is made of, which decides how it fares lying on the ground. Every catalogue item names one.
   *
   * A varchar rather than the native enum Hibernate makes on MariaDB, which `ddl-auto: update` never widens.
   */
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "material", nullable = false, length = 16)
  var material: ItemMaterial,

  /**
   * How many tiles a ranged weapon shoots. Set together with [ammoType], which for a weapon is the kind it fires;
   * together they are what makes a weapon ranged.
   */
  @Column(name = "attack_range", nullable = true)
  var attackRange: Int? = null,

  /** What a tool is for, like felling; null for anything that is not a tool. */
  @Enumerated(EnumType.STRING)
  @Column(name = "tool_kind", nullable = true, length = 16)
  var toolKind: ToolKind? = null,

  /** What kind of weapon this is, for profession and skill checks; null for anything that is not a weapon. */
  @Enumerated(EnumType.STRING)
  @Column(name = "weapon_type", nullable = true, length = 16)
  var weaponType: WeaponType? = null,

  /** A weapon that needs both hands: nothing may be worn in [EquipmentSlot.LEFT_HAND] beside it. */
  @Column(name = "two_handed", nullable = false)
  var twoHanded: Boolean = false,

  /** For [ItemType.AMMO], the kind of ammunition it is. For a ranged weapon, the kind it fires. */
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "ammo_type", nullable = true, length = 16)
  var ammoType: AmmoType? = null,

  /** The element an [ItemType.AMMO] item lends the shot. Level 1 only, as for every attack element. */
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "element", nullable = true, length = 16)
  var element: Element? = null,

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
  fun effectiveLevel(upgradeLevel: Int): Int = baseLevel + upgradeLevel

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

    // The rule is about what may be worn or used, so there is nothing for it to mean on a material - and a
    // flag that silently does nothing is worse than a boot that says so.
    require(!noviceOnly || type != ItemType.ETC) {
      "Item $identifier is $type and must not declare novice-only"
    }

    require(armorType == null || type == ItemType.EQUIP) {
      "Item $identifier is $type and must not declare an armor-type"
    }

    require(type != ItemType.AMMO || ammoType != null) {
      "Item $identifier is AMMO and must declare its ammo-type"
    }

    require(type == ItemType.AMMO || (ammoType == null) == (attackRange == null)) {
      "Item $identifier is a ranged weapon only with both an ammo-type and an attack-range"
    }

    require(attackRange == null || (type == ItemType.EQUIP && equipSlot == EquipmentSlot.RIGHT_HAND)) {
      "Item $identifier declares an attack-range but is no RIGHT_HAND weapon"
    }

    require(weaponType == null || (type == ItemType.EQUIP && equipSlot == heldSlotOf(weaponType!!))) {
      "Item $identifier is a ${weaponType} and must be equipment held in the ${weaponType?.let(::heldSlotOf)}"
    }

    require(weaponType == null || weaponType?.twoHanded == twoHanded) {
      "Item $identifier is a ${weaponType}, which takes ${weaponType?.hands} hand(s), but two-handed is $twoHanded"
    }

    require(!twoHanded || equipSlot == EquipmentSlot.RIGHT_HAND) {
      "Item $identifier is two-handed but is not worn in the RIGHT_HAND"
    }

    require(element == null || type == ItemType.AMMO) {
      "Item $identifier is $type and must not declare an element"
    }

    require(element == null || ElementModifier.isLegalAttackElement(element!!)) {
      "Item $identifier declares element $element, but only a level 1 element can be shot"
    }
  }

  /** An off-hand weapon, a tome, is held in the left hand; every other weapon in the right. */
  private fun heldSlotOf(weaponType: WeaponType): EquipmentSlot {
    return if (weaponType.isOffHand) EquipmentSlot.LEFT_HAND else EquipmentSlot.RIGHT_HAND
  }

  /** Stored by ordinal: append, never reorder. */
  enum class ItemType {
    USABLE,
    EQUIP,
    ETC,

    /** Pointed at from the quiver and spent per shot, never worn. */
    AMMO
  }
}
