package net.bestia.zone.battle.status

import net.bestia.zone.battle.gear.ToolKind
import net.bestia.zone.battle.gear.ArmorType
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.battle.BestiaKind
import net.bestia.zone.battle.damage.DamageModifiers
import net.bestia.zone.battle.damage.HitForm
import net.bestia.zone.battle.damage.HitReach
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.RangedWeapon
import net.bestia.zone.battle.ecs.status.BaseStatusValues

/**
 * Mutable working set a [StatusEffectScript] writes into while
 * [net.bestia.zone.battle.ecs.effects.StatusValueRecalcSystem] rebuilds an entity's effective
 * status values from scratch. Seeded from the entity's permanent attributes - a master's
 * [BaseStatusValues], a mob's species - plus whatever other base values feed
 * into the recalc (currently just [baseSpeed]); starts equal to the unbuffed values, then every
 * learned passive skill, worn item and active effect mutates it in turn.
 *
 * The regeneration modifiers are the one part not seeded from a base value: they start neutral and
 * only accumulate contributions, because the *base* regeneration rate is derived separately by
 * [RegenerationCalculator] from the pool size and attributes at the moment regen actually ticks.
 * They are exposed as [RegenModifier] values with `private set` plus `add*` methods rather than as
 * mutable fields, so one script cannot silently discard another's contribution by assigning.
 *
 * The four combat terms - [attack], [magicAttack], [hardDefense], [hardMagicDefense] - start at zero for
 * the same reason and are protected the same way. There is no *base* weapon or equipment defence to seed
 * them from: a bare-handed fighter's power is the attribute-derived term the damage formula computes
 * separately, and armour that is not worn contributes nothing rather than a baseline.
 */
class StatusValueRecalcContext(
  base: StatusValues,
  baseSpeed: Float
) {

  /** For an owner, whose permanent attributes live in a [BaseStatusValues] component of their own. */
  constructor(base: BaseStatusValues, baseSpeed: Float) : this(base.toAttributes(), baseSpeed)

  var strength: Int = base.strength
  var intelligence: Int = base.intelligence
  var vitality: Int = base.vitality
  var dexterity: Int = base.dexterity
  var willpower: Int = base.willpower
  var agility: Int = base.agility
  var speed: Float = baseSpeed

  var attack: Int = 0
    private set

  var magicAttack: Int = 0
    private set

  var hardDefense: Int = 0
    private set

  var hardMagicDefense: Int = 0
    private set

  var hit: Int = 0
    private set

  var flee: Int = 0
    private set

  var crit: Int = 0
    private set

  /** Percentage points on top of the formula's max HP and max mana: the docs' `HPModPerc` / `ManaModPerc`. */
  var maxHpPercent: Int = 0
    private set

  var maxManaPercent: Int = 0
    private set

  /** Percentage points of damage removed per element, keyed by the element without its level. */
  var elementResistance: Map<Element, Int> = emptyMap()
    private set

  /** Percentage points taken off every cast time. */
  var castTimeReduction: Int = 0
    private set

  /** The element every hit on this entity is weighed against instead of its own, like a freeze's Water 1. */
  var elementOverride: Element? = null

  /** The element a plain melee blow hits with instead of Normal, like a blessed weapon's Holy. */
  var weaponElement: Element? = null

  /** Damage reduction points added to both hard defences, see [DefenseValues.withHardDefenseRaisedBy]. */
  var defensePercent: Int = 0
    private set

  /** Percent of the physical defence taken away, see [DefenseValues.withPhysicalDefenseCutBy]. */
  var defenseCutPercent: Int = 0
    private set

  /** Percentage points on top of the resolved attack, hit rate and attack speed. */
  var attackPercent: Int = 0
    private set

  /** Percentage points on top of the magic attack alone, beside [attackPercent]. */
  var magicAttackPercent: Int = 0
    private set

  var hitPercent: Int = 0
    private set

  var aspdPercent: Int = 0
    private set

  /** Percentage points on every heal this entity receives, see `Healing`. */
  var healReceivedPercent: Int = 0
    private set

  /** Refinement of the weapon in hand. It scales the weapon term of the damage formula, not the total. */
  var weaponUpgradeLevel: Int = 0

  /** ATK of a second weapon in the left hand. It strikes in a blow of its own, so it is kept apart from [attack]. */
  var offHandAttack: Int = 0
    private set
  var offHandUpgradeLevel: Int = 0

  /** How hard the second weapon strikes, in percent of a blow; 0 while nothing lets it swing. Set by Dual Wield. */
  var offHandDamagePercent: Int = 0

  var rangedWeapon: RangedWeapon? = null

  /**
   * What the entity holds and wears, for a passive that only counts with the right gear - Iron Fist with fist
   * weapons, Heavy Armor Mastery in plate. Filled by the equipment before the passives run.
   */
  var heldWeaponType: WeaponType? = null
  var heldTool: ToolKind? = null
  var offHandWeaponType: WeaponType? = null
  var bodyArmorType: ArmorType? = null

  /** The weight of a shield in the left hand, null for no shield. */
  var shieldArmorType: ArmorType? = null

  /** The weight of every worn piece that has one, the shield included. */
  var wornArmorTypes: List<ArmorType> = emptyList()

  var hpRegen: RegenModifier = RegenModifier()
    private set

  /** Potency's Focus term: what gear and buffs add to the strength of this entity's casts. Capped when used. */
  var potencyFocus: Int = 0

  /** See [DamageModifiers]: bonuses to damage dealt and reductions to damage taken, by the other side. */
  var damageModifiers: DamageModifiers = DamageModifiers.NONE
    private set

  var manaRegen: RegenModifier = RegenModifier()
    private set

  var staminaRegen: RegenModifier = RegenModifier()
    private set

  /**
   * Adds to this entity's HP regeneration: [flat] extra points per tick, and/or [percent] percentage
   * points on top of the resolved rate. Named arguments are the point of the signature - `flat` and
   * `percent` are trivially transposed at a call site, and `addHpRegen(percent = 3 * level)` cannot
   * be misread the way a positional `3 * level` could.
   */
  fun addHpRegen(flat: Int = 0, percent: Int = 0) {
    hpRegen = hpRegen.plus(flat = flat, percent = percent)
  }

  /** HP and mana keep regenerating in combat, where they would otherwise stop. */
  fun regenerateInCombat() {
    hpRegen = hpRegen.copy(inCombat = true)
    manaRegen = manaRegen.copy(inCombat = true)
  }

  /** Percent less stamina a skill costs, and percent less the weather and the road drain. */
  var staminaCostReduction: Int = 0
  var staminaDrainReduction: Int = 0

  /** Moving while casting at this share of the walking speed, in percent; 0 means a move breaks the cast. */
  var castMovePercent: Int = 0

  /** Percent chance a blow does not interrupt a cast. */
  var castInterruptResistance: Int = 0

  /** Statuses this entity shrugs off while the source of the immunity lasts, beside its species' own. */
  var statusImmunities: Set<StatusEffectId> = emptySet()

  fun addStatusImmunities(vararg effects: StatusEffectId) {
    statusImmunities = statusImmunities + effects
  }

  /** Percentage points of resistance to a status, beside what the attributes give. See `StatusResistance`. */
  var statusResistance: Map<StatusEffectId, Int> = emptyMap()
    private set

  fun addStatusResistance(effect: StatusEffectId, percent: Int) {
    statusResistance = statusResistance + (effect to (statusResistance[effect] ?: 0) + percent)
  }

  /** Percent more weight the entity can carry, beside what strength and vitality give. */
  var carryCapacityPercent: Int = 0

  /** Cannot be pushed back by a blow or a blast. A dash of its own still moves it. */
  var knockbackImmune: Boolean = false

  /** Stops natural regeneration outright; percentages add up, so -100% alone cannot promise that. */
  fun blockRegen(hp: Boolean = false, mana: Boolean = false) {
    if (hp) hpRegen = hpRegen.copy(blocked = true)
    if (mana) manaRegen = manaRegen.copy(blocked = true)
  }

  fun addManaRegen(flat: Int = 0, percent: Int = 0) {
    manaRegen = manaRegen.plus(flat = flat, percent = percent)
  }

  fun addStaminaRegen(flat: Int = 0, percent: Int = 0) {
    staminaRegen = staminaRegen.plus(flat = flat, percent = percent)
  }

  /**
   * Adds flat attack power, the term a weapon carries.
   *
   * **Un-refined**: `Weapon.physicalAtk` already adds the refinement on top, so a script that scaled this by its
   * own upgrade level would charge for refinement twice, and the curve would then live in as many places as there
   * are weapons.
   */
  fun addAttack(atk: Int = 0, matk: Int = 0) {
    attack += atk
    magicAttack += matk
  }

  /** Runs [wearOffHand], booking the ATK it adds to the second weapon instead of the main one. */
  fun bookAttackToOffHand(wearOffHand: () -> Unit) {
    val mainAttack = attack
    wearOffHand()
    offHandAttack += attack - mainAttack
    attack = mainAttack
  }

  /** Adds to the docs' `HitModSum`, `FleeModSum` and the CRIT bonus, on top of the attribute-derived values. */
  fun addAccuracy(hit: Int = 0, flee: Int = 0, crit: Int = 0) {
    this.hit += hit
    this.flee += flee
    this.crit += crit
  }

  fun addMaxPools(hpPercent: Int = 0, manaPercent: Int = 0) {
    maxHpPercent += hpPercent
    maxManaPercent += manaPercent
  }

  fun addElementResistance(element: Element, percent: Int) {
    val base = element.base
    elementResistance = elementResistance + (base to (elementResistance[base] ?: 0) + percent)
  }

  fun addPercent(attack: Int = 0, defense: Int = 0, hit: Int = 0, aspd: Int = 0, healReceived: Int = 0) {
    attackPercent += attack
    defensePercent += defense
    hitPercent += hit
    aspdPercent += aspd
    healReceivedPercent += healReceived
  }

  fun cutDefense(percent: Int) {
    defenseCutPercent += percent
  }

  /** Percent of the soft magic defence taken away, see [DefenseValues.withSoftMagicDefenseCutBy]. */
  var magicDefenseCutPercent: Int = 0
    private set

  fun cutMagicDefense(percent: Int) {
    magicDefenseCutPercent += percent
  }

  fun addMagicAttackPercent(percent: Int) {
    magicAttackPercent += percent
  }

  /** Percent more damage from the spells of raw mana, Soul Break and Mindbreak, see Arcane Mastery. */
  var arcaneDamagePercent: Int = 0

  /** More damage dealt to a [kind], an [element], a boss or the unholy, in percent; see [DamageModifiers]. */
  fun addDamageDealt(
    percent: Int,
    kind: BestiaKind? = null,
    element: Element? = null,
    boss: Boolean = false,
    unholy: Boolean = false,
  ) {
    val current = damageModifiers
    damageModifiers = current.copy(
      dealtVsKind = if (kind != null) current.dealtVsKind.plusPercent(kind, percent) else current.dealtVsKind,
      dealtVsElement = if (element != null) current.dealtVsElement.plusPercent(element.base, percent) else current.dealtVsElement,
      dealtVsBoss = if (boss) current.dealtVsBoss + percent else current.dealtVsBoss,
      dealtVsUnholy = if (unholy) current.dealtVsUnholy + percent else current.dealtVsUnholy,
    )
  }

  /**
   * Less damage taken, in percent: from a [kind], a boss or the unholy, of a [form] (physical, magic) or [reach]
   * (melee, ranged), or from [anyHit]. Iron Skin is `addDamageTaken(3 * level, form = HitForm.PHYSICAL)`. See
   * [DamageModifiers].
   */
  fun addDamageTaken(
    percent: Int,
    kind: BestiaKind? = null,
    boss: Boolean = false,
    form: HitForm? = null,
    reach: HitReach? = null,
    unholy: Boolean = false,
    anyHit: Boolean = false,
  ) {
    val current = damageModifiers
    damageModifiers = current.copy(
      takenFromKind = if (kind != null) current.takenFromKind.plusPercent(kind, percent) else current.takenFromKind,
      takenFromBoss = if (boss) current.takenFromBoss + percent else current.takenFromBoss,
      takenFromUnholy = if (unholy) current.takenFromUnholy + percent else current.takenFromUnholy,
      takenByForm = if (form != null) current.takenByForm.plusPercent(form, percent) else current.takenByForm,
      takenByReach = if (reach != null) current.takenByReach.plusPercent(reach, percent) else current.takenByReach,
      takenFromAnyHit = if (anyHit) current.takenFromAnyHit + percent else current.takenFromAnyHit,
    )
  }

  private fun <K> Map<K, Int>.plusPercent(key: K, percent: Int): Map<K, Int> {
    return this + (key to (this[key] ?: 0) + percent)
  }

  fun addCastTimeReduction(percent: Int) {
    castTimeReduction += percent
  }

  /**
   * Adds equipment ("hard") defence, in percentage points of damage removed - 3 means 3% less. Ragnarok
   * Online's pre-renewal split, which the rest of the damage package already follows: this is the
   * multiplicative kind, and the flat kind is derived from attributes by
   * [net.bestia.zone.battle.status.DefenseValues] rather than contributed here.
   */
  fun addDefense(hardDefense: Int = 0, hardMagicDefense: Int = 0) {
    this.hardDefense += hardDefense
    this.hardMagicDefense += hardMagicDefense
  }
}
