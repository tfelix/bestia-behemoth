package net.bestia.zone.battle.damage

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.FixedRandom
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What the formula pays out across the level range, for two evenly-matched fighters.
 *
 * Not a balance assertion - balance is a design decision and belongs to whoever tunes the game. This pins the
 * properties that make the formula *usable* at all, the first of which the old placeholder violated outright: a
 * fight between equals has to be decided by more than the minimum-damage floor.
 */
class PhysicalDamageCurveTest {

  /** Half the variance, so each figure is the middle of its swing's range rather than a lucky end of it. */
  private val calculator = MeleePhysicalDamageCalculator(FixedRandom(0.5f))

  @Test
  fun `an even fight is never decided by the minimum-damage floor alone`() {
    val tooSmall = LEVELS.filter { damageAt(it) <= BaseDamageCalculator.MIN_DAMAGE }

    assertTrue(
      tooSmall.isEmpty(),
      "two equals cannot hurt each other beyond the floor at ${tooSmall.joinToString { "Lv$it" }} " +
        "(curve: ${LEVELS.joinToString { "Lv$it=${damageAt(it)}" }})"
    )
  }

  @Test
  fun `damage grows with level, but slower than the attributes feeding it`() {
    // A curve that outpaced its inputs would turn a small level gap into a one-shot. Attributes go up
    // elevenfold between Lv.10 and Lv.100 here; damage must not.
    val low = damageAt(10)
    val high = damageAt(100)

    assertTrue(high > low, "damage should grow with level ($low -> $high)")
    assertTrue(high < low * 11, "the curve has run away ($low -> $high)")
  }

  @Test
  fun `a level advantage is worth something without being decisive`() {
    val even = damageAt(50)
    val uphill = damage(attackerLevel = 40, defenderLevel = 60)
    val downhill = damage(attackerLevel = 60, defenderLevel = 40)

    assertTrue(downhill > even, "hitting down should hurt more ($even -> $downhill)")
    assertTrue(uphill < even, "hitting up should hurt less ($even -> $uphill)")
    assertTrue(uphill > BaseDamageCalculator.MIN_DAMAGE, "hitting up should still be worth doing, was $uphill")
  }

  private fun damageAt(level: Int): Int = damage(level, level)

  private fun damage(attackerLevel: Int, defenderLevel: Int): Int {
    val ctx = BattleContextFixture.entityCtx(
      attackerEntity = fighter(attackerLevel, BattleContextFixture.ATTACKER_ID),
      defenderEntity = fighter(defenderLevel, BattleContextFixture.DEFENDER_ID)
    ) as EntityBattleContext

    // Bare-handed: the fixture arms its attacker, and no equipment system exists to arm a real one.
    return calculator.calculateDamage(ctx.copy(weapon = ctx.weapon.copy(atk = 0)), isCritical = false)
  }

  /** Every attribute rising about a point a level, which is the shape a character's career has. */
  private fun fighter(level: Int, id: Long) = BattleContextFixture.battleEntity(
    level = level,
    strength = 10 + level,
    dexterity = 10 + level,
    agility = 10 + level,
    vitality = 10 + level,
    willpower = 10 + level,
    intelligence = 10 + level,
    id = id
  )

  private companion object {
    val LEVELS = listOf(1, 5, 10, 25, 50, 75, 100)
  }
}
