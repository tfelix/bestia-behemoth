package net.bestia.zone.battle.status

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegenerationCalculatorTest {

  private val calculator = RegenerationCalculator()
  private val pools = ConditionValueCalculator()

  @Test
  fun `hp regeneration grows with vitality and pool size`() {
    val base = calculator.hpRegen(maxHp = 400, vitality = 10)

    assertTrue(calculator.hpRegen(maxHp = 400, vitality = 50) > base, "higher VIT must raise HP regen")
    assertTrue(calculator.hpRegen(maxHp = 1200, vitality = 10) > base, "a bigger pool must raise HP regen")
  }

  @Test
  fun `mana regeneration grows with intelligence and pool size`() {
    val base = calculator.manaRegen(maxMana = 400, intelligence = 10)

    assertTrue(calculator.manaRegen(maxMana = 400, intelligence = 50) > base, "higher INT must raise mana regen")
    assertTrue(calculator.manaRegen(maxMana = 1200, intelligence = 10) > base, "a bigger pool must raise mana regen")
  }

  @Test
  fun `stamina regeneration grows with vitality, willpower and pool size`() {
    val base = calculator.staminaRegen(maxStamina = 400, vitality = 10, willpower = 10)

    assertTrue(calculator.staminaRegen(maxStamina = 400, vitality = 60, willpower = 10) > base)
    assertTrue(calculator.staminaRegen(maxStamina = 400, vitality = 10, willpower = 80) > base)
    assertTrue(calculator.staminaRegen(maxStamina = 1200, vitality = 10, willpower = 10) > base)
  }

  @Test
  fun `every pool regenerates at least one point`() {
    // The docs' max(1, ...) floor: a tiny pool still ticks upwards rather than stalling forever,
    // which is what a plain floor(MaxHP / 200) would do at low level.
    assertEquals(1, calculator.hpRegen(maxHp = 1, vitality = 0))
    assertEquals(1, calculator.manaRegen(maxMana = 1, intelligence = 0))
    assertEquals(1, calculator.staminaRegen(maxStamina = 1, vitality = 0, willpower = 0))
  }

  @Test
  fun `formulas match the documented values for a balanced level 1 master`() {
    // Locks the integer-division behavior so a formula tweak is a conscious change, and pins the
    // amounts against the pools a real level-1 master actually has (effort values 9 across the
    // board, the balanced creation spread).
    val maxHp = pools.computeMaxHp(level = 1, vitality = 9)
    val maxMana = pools.computeMaxMana(level = 1, intelligence = 9)
    val maxStamina = pools.computeMaxStamina(level = 1, vitality = 9, strength = 9, willpower = 9)
    assertEquals(18, maxHp)
    assertEquals(28, maxMana)
    assertEquals(27, maxStamina)

    assertEquals(2, calculator.hpRegen(maxHp, vitality = 9))
    assertEquals(2, calculator.manaRegen(maxMana, intelligence = 9))
    assertEquals(3, calculator.staminaRegen(maxStamina, vitality = 9, willpower = 9))
  }

  @Test
  fun `hp regeneration stays bounded at high level`() {
    // The regression this calculator exists for. The previous inline expression was
    // `max * vit / 99.0 + 2.0 / 100.0`, i.e. `(max * vit / 99) + 0.02` - which at this pool and
    // attribute yields 394 per 6s tick, healing a character from empty to full in one tick. The
    // documented rate is 21.
    val maxHp = pools.computeMaxHp(level = 100, vitality = 100)
    assertEquals(390, maxHp)

    assertEquals(21, calculator.hpRegen(maxHp, vitality = 100))
  }

  @Test
  fun `mana regeneration gains the documented bonus from 120 intelligence upwards`() {
    val maxMana = pools.computeMaxMana(level = 100, intelligence = 130)
    assertEquals(379, maxMana)

    // 1 + 379/100 + 130/6 = 25, plus the high-INT bonus of 4 + (130 - 120)/2 = 9.
    assertEquals(34, calculator.manaRegen(maxMana, intelligence = 130))
  }

  @Test
  fun `a null modifier leaves the base rate untouched`() {
    // The common case by a wide margin: nothing worn, buffed or learned affects this pool, so the
    // entity carries no RegenerationModifiers component at all.
    assertEquals(12, calculator.applyModifier(12, null))
  }

  @Test
  fun `a flat modifier is added before the percentage is applied`() {
    // (12 + 5) * 1.20, not 12 * 1.20 + 5. Same ordering rationale as applying equipment before
    // status effects: a percentage bonus scales the geared value, not the naked one.
    assertEquals(20, calculator.applyModifier(12, RegenModifier(flat = 5, percent = 20)))
  }

  @Test
  fun `percentages accumulate additively`() {
    val stacked = RegenModifier()
      .plus(percent = 6)
      .plus(percent = 10)
      .plus(percent = 4)

    assertEquals(RegenModifier(percent = 20), stacked)
    assertEquals(calculator.applyModifier(50, RegenModifier(percent = 20)), calculator.applyModifier(50, stacked))
  }

  @Test
  fun `a suppression debuff can stop regeneration but never invert it`() {
    // -100% zeroes the rate, which is the point of such a debuff. Beyond that the product itself
    // goes negative, and Kotlin's integer division truncates toward zero rather than flooring - so
    // without the clamp a strong enough debuff would start *healing*. A regen system only ever adds
    // what this returns.
    assertEquals(0, calculator.applyModifier(20, RegenModifier(percent = -100)))
    assertEquals(0, calculator.applyModifier(20, RegenModifier(percent = -200)))
    assertEquals(0, calculator.applyModifier(2, RegenModifier(flat = -10)))
  }

  @Test
  fun `a small percentage of a small base is swallowed by integer truncation`() {
    // A deliberate, documented property rather than a bug: the docs floor this result, and the
    // placeholder base values keep low-level pools tiny. A level-1 master regenerates 2 HP a tick,
    // so INNER_PEACE (+3%/level, maxLevel 10) is worth nothing at all until +50% - which the skill
    // cannot reach. A percentage passive is inherently a late-game passive while this holds.
    val levelOneBase = calculator.hpRegen(pools.computeMaxHp(level = 1, vitality = 9), vitality = 9)
    assertEquals(2, levelOneBase)

    assertEquals(2, calculator.applyModifier(levelOneBase, RegenModifier(percent = 3)))
    assertEquals(2, calculator.applyModifier(levelOneBase, RegenModifier(percent = 30)))
    assertEquals(3, calculator.applyModifier(levelOneBase, RegenModifier(percent = 50)))
  }

  @Test
  fun `a percentage modifier is felt at a late-game regeneration rate`() {
    // The same +30% that vanishes at level 1 is worth a real +6 on a level-100 pool.
    val lateGameBase = calculator.hpRegen(pools.computeMaxHp(level = 100, vitality = 100), vitality = 100)
    assertEquals(21, lateGameBase)

    assertEquals(27, calculator.applyModifier(lateGameBase, RegenModifier(percent = 30)))
  }

  @Test
  fun `the high intelligence bonus is a step, not a ramp`() {
    // Deliberately discontinuous per the docs: crossing 120 INT is worth more than the point before
    // it. Guards against someone "smoothing" the threshold away.
    val below = calculator.manaRegen(maxMana = 300, intelligence = 119)
    val atThreshold = calculator.manaRegen(maxMana = 300, intelligence = 120)

    assertEquals(5, atThreshold - below)
  }
}
