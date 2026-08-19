package net.bestia.zone.battle.damage

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.FixedRandom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The physical damage formula, melee and ranged.
 *
 * Every case pins the variance roll with [FixedRandom] so a number can be asserted rather than a range: the
 * formula's own randomness is the one thing that is not interesting to test.
 */
class PhysicalDamageCalculatorTest {

  /** No variance at all, so `baseAtk` is exactly its ceiling and the arithmetic is checkable by hand. */
  private val melee = MeleePhysicalDamageCalculator(FixedRandom(0f))
  private val ranged = RangedPhysicalDamageCalculator(FixedRandom(0f))

  @Test
  fun `an unarmed swing works out to twice ATK less the target's soft defence`() {
    // Attacker at Lv.10 with every attribute 10: ATK = 10/4 + 10 + 10/5 + 10/3 = 17, so baseAtk = 34.
    // Defender's SoftDEF = 10 + 10/5 + 10/5 + 10/4 = 16.
    val damage = melee.calculateDamage(ctx(), isCritical = false)

    assertEquals(34 - 16, damage)
  }

  @Test
  fun `strength drives a melee swing`() {
    val weak = melee.calculateDamage(ctx(attackerStrength = 10), isCritical = false)
    val strong = melee.calculateDamage(ctx(attackerStrength = 60), isCritical = false)

    assertTrue(strong > weak, "melee should scale with STR ($weak -> $strong)")
  }

  @Test
  fun `dexterity drives a ranged shot, and strength barely does`() {
    val base = ranged.calculateDamage(ctx(), isCritical = false)
    val dextrous = ranged.calculateDamage(ctx(attackerDexterity = 60), isCritical = false)
    val burly = ranged.calculateDamage(ctx(attackerStrength = 60), isCritical = false)

    assertTrue(dextrous > base, "ranged should scale with DEX ($base -> $dextrous)")
    assertTrue(
      dextrous - base > burly - base,
      "DEX should matter more to an archer than STR (+${dextrous - base} vs +${burly - base})"
    )
  }

  @Test
  fun `the same attacker hits a tougher target for less`() {
    val soft = melee.calculateDamage(ctx(defenderDefense = 0), isCritical = false)
    val armoured = melee.calculateDamage(ctx(defenderDefense = 20), isCritical = false)

    assertEquals(20, soft - armoured, "soft defence is subtracted flat, so the gap is exactly the difference")
  }

  @Test
  fun `armour tells more against an arrow than against a sword`() {
    // The ranged surcharge: VIT/2 + STR/6 on top of the shared SoftDEF. Same attacker power both ways, so any
    // difference is defence. Compared at equal ATK by giving STR and DEX the same value.
    val bySword = melee.calculateDamage(ctx(), isCritical = false)
    val byArrow = ranged.calculateDamage(ctx(), isCritical = false)

    assertTrue(byArrow < bySword, "an arrow should be blunted more by the same target ($byArrow vs $bySword)")
  }

  @Test
  fun `a critical adds forty percent and ignores defence entirely`() {
    val normal = melee.calculateDamage(ctx(), isCritical = false)
    val critical = melee.calculateDamage(ctx(), isCritical = true)

    // 34 * 1.4 with no subtraction, against 34 - 16 for the ordinary hit.
    assertEquals(47, critical)
    assertEquals(18, normal)
    assertTrue(
      critical > normal * 2,
      "bypassing defence should matter more than the 40% bonus alone ($normal -> $critical)"
    )
  }

  @Test
  fun `defence a critical bypasses cannot bring it down at all`() {
    val vsSoft = melee.calculateDamage(ctx(defenderDefense = 0), isCritical = true)
    val vsArmoured = melee.calculateDamage(ctx(defenderDefense = 500), isCritical = true)

    assertEquals(vsSoft, vsArmoured, "a critical ignores soft defence, however much of it there is")
  }

  @Test
  fun `a hit that armour would have swallowed still costs a point`() {
    val damage = melee.calculateDamage(
      ctx(attackerLevel = 1, attackerStrength = 1, defenderDefense = 500),
      isCritical = false
    )

    assertEquals(BaseDamageCalculator.MIN_DAMAGE, damage, "a hit that landed always hurts")
  }

  @Test
  fun `an element the target resists takes damage off, and one it fears puts damage on`() {
    // Water against Fire is 150 in ElementModifier's table; Water against Water is 25.
    val vsFire = melee.calculateDamage(
      ctx(attackElement = Element.WATER, defenderElement = Element.FIRE, defenderDefense = 0),
      isCritical = false
    )
    val vsWater = melee.calculateDamage(
      ctx(attackElement = Element.WATER, defenderElement = Element.WATER, defenderDefense = 0),
      isCritical = false
    )

    assertEquals(51, vsFire, "34 * 1.5")
    assertEquals(8, vsWater, "34 * 0.25, floored")
  }

  @Test
  fun `a shielding defence modifier reduces damage without touching the flat term`() {
    val unshielded = melee.calculateDamage(ctx(defenderDefense = 0), isCritical = false)
    val shielded = melee.calculateDamage(
      ctx(defenderDefense = 0, physicalDefenseMod = 2f),
      isCritical = false
    )

    assertEquals(unshielded / 2, shielded, "a mod of 2 halves what gets through")
  }

  @Test
  fun `a pierced defence lets more through, not merely all of it`() {
    val plain = melee.calculateDamage(ctx(defenderDefense = 0), isCritical = false)
    val pierced = melee.calculateDamage(
      ctx(defenderDefense = 0, physicalDefenseMod = 0.5f),
      isCritical = false
    )

    assertEquals(plain * 2, pierced, "a mod of 0.5 is half the defence, so twice the damage")
  }

  @Test
  fun `no amount of piercing turns a swing into a one-shot`() {
    val plain = melee.calculateDamage(ctx(defenderDefense = 0), isCritical = false)
    val absurd = melee.calculateDamage(
      ctx(defenderDefense = 0, physicalDefenseMod = 0f),
      isCritical = false
    )

    assertEquals(plain * 4, absurd, "piercing caps at fourfold, however far the modifier is driven")
  }

  @Test
  fun `no stack of shields makes an entity immune`() {
    // A strong attacker on purpose: at the fixture's default power the 5% floor and the 1-damage floor land on
    // the same number, so the test would pass with the shield cap removed entirely and prove nothing.
    // STR 60 gives baseAtk 134, so 5% of it is 6 - distinguishable from the 1 it would be without the cap.
    val damage = melee.calculateDamage(
      ctx(attackerStrength = 60, defenderDefense = 0, physicalDefenseMod = 1000f),
      isCritical = false
    )

    assertEquals(6, damage, "the shield floor lets 5% through, well clear of the minimum-damage floor")
  }

  @Test
  fun `a critical against a target whose armour was stripped is still the better outcome`() {
    // The modifier carries piercing as well as shielding, so "a crit ignores defence" has to mean at least
    // neutral rather than exactly neutral - otherwise critting a stripped target would be a penalty.
    val pierced = ctx(defenderDefense = 0, physicalDefenseMod = 0.5f)

    val ordinary = melee.calculateDamage(pierced, isCritical = false)
    val critical = melee.calculateDamage(pierced, isCritical = true)

    assertTrue(critical > ordinary, "a crit must never deal less than the swing it replaced ($ordinary -> $critical)")
  }

  @Test
  fun `variance only ever takes damage off its ceiling`() {
    val ceiling = MeleePhysicalDamageCalculator(FixedRandom(0f)).calculateDamage(ctx(), isCritical = false)
    val floor = MeleePhysicalDamageCalculator(FixedRandom(1f)).calculateDamage(ctx(), isCritical = false)

    assertTrue(floor < ceiling, "a full variance roll should reduce damage ($ceiling -> $floor)")
    assertTrue(floor >= BaseDamageCalculator.MIN_DAMAGE)
  }

  @Test
  fun `a level 2 element cannot be swung, and is refused where it is chosen`() {
    // ElementModifier only tabulates level 1 attack elements, and it is consulted from the tick thread - so the
    // refusal has to happen at construction, not inside the formula.
    val ex = assertThrows<IllegalArgumentException> {
      BattleContextFixture.attack(element = Element.FIRE_2)
    }

    assertTrue(ex.message!!.contains("FIRE_2"), "the message should name the element: ${ex.message}")
  }

  private fun ctx(
    attackerLevel: Int = 10,
    attackerStrength: Int = 10,
    attackerDexterity: Int = 10,
    defenderDefense: Int? = null,
    defenderElement: Element = Element.NORMAL,
    attackElement: Element = Element.NORMAL,
    physicalDefenseMod: Float = 1f,
  ): EntityBattleContext {
    val ctx = BattleContextFixture.entityCtx(
      attack = BattleContextFixture.attack(element = attackElement),
      attackerEntity = BattleContextFixture.battleEntity(
        level = attackerLevel,
        strength = attackerStrength,
        dexterity = attackerDexterity
      ),
      defenderEntity = BattleContextFixture.battleEntity(
        defense = defenderDefense,
        element = defenderElement,
        id = BattleContextFixture.DEFENDER_ID
      )
    ) as EntityBattleContext

    // Bare-handed on purpose: the fixture arms its attacker with a 10-ATK weapon, and there is no equipment
    // system, so a weapon term here would be testing a number no real fight can produce yet.
    return ctx.copy(
      weapon = ctx.weapon.copy(atk = 0),
      damageVariables = ctx.damageVariables.copy(physicalDefenseMod = physicalDefenseMod)
    )
  }
}
