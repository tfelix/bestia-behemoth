package net.bestia.zone.battle.damage

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.FixedRandom
import net.bestia.zone.battle.Size
import net.bestia.zone.battle.SizeModifier
import net.bestia.zone.battle.Weapon
import net.bestia.zone.battle.gear.WeaponType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SizeModifierDamageTest {

  private val melee = MeleePhysicalDamageCalculator(FixedRandom(0f))
  private val magic = MagicDamageCalculator(FixedRandom(0f))

  private fun ctx(type: WeaponType?, size: Size): EntityBattleContext {
    val base = BattleContextFixture.entityCtx() as EntityBattleContext

    return base.copy(
      weapon = Weapon(atk = 100, matk = 100, upgradeLevel = 0, type = type),
      defender = base.defender.copy(size = size),
    )
  }

  @Test
  fun `a dagger bites a small body harder than a big one`() {
    val small = melee.calculateDamage(ctx(WeaponType.DAGGER, Size.SMALL), isCritical = false)
    val big = melee.calculateDamage(ctx(WeaponType.DAGGER, Size.BIG), isCritical = false)

    // Only the weapon's 100 attack is weighed: half of it is lost on the big body.
    assertEquals(50, small - big)
  }

  @Test
  fun `a great axe is the other way round`() {
    assertTrue(
      melee.calculateDamage(ctx(WeaponType.GREATAXE, Size.BIG), isCritical = false) >
        melee.calculateDamage(ctx(WeaponType.GREATAXE, Size.SMALL), isCritical = false)
    )
  }

  @Test
  fun `bare hands hit every size alike`() {
    val small = melee.calculateDamage(ctx(null, Size.SMALL), isCritical = false)
    val big = melee.calculateDamage(ctx(null, Size.BIG), isCritical = false)

    assertEquals(small, big)
  }

  @Test
  fun `a spell does not care about size`() {
    assertEquals(
      magic.calculateDamage(ctx(WeaponType.DAGGER, Size.SMALL)),
      magic.calculateDamage(ctx(WeaponType.DAGGER, Size.BIG))
    )
  }

  @Test
  fun `every weapon type has a row`() {
    for (type in WeaponType.entries) {
      for (size in Size.entries) assertTrue(SizeModifier.percentOf(type, size) in 50..100, "$type vs $size")
    }
  }

  @Test
  fun `a species without a weight takes its size's default, a boss twice that`() {
    assertEquals(15, Size.SMALL.defaultBodyWeightKg(boss = false))
    assertEquals(60, Size.MEDIUM.defaultBodyWeightKg(boss = false))
    assertEquals(500, Size.BIG.defaultBodyWeightKg(boss = true))
  }
}
