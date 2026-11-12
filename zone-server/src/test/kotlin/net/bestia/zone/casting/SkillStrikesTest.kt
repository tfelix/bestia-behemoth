package net.bestia.zone.casting

import io.mockk.mockk
import net.bestia.zone.battle.BattleContextFactory
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.FixedRandom
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.Weapon
import net.bestia.zone.battle.attack.AttackStrategyFactory
import net.bestia.zone.battle.attack.BattleAttack
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PropPromotionService
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SkillStrikesTest {

  private val world = testWorld()
  private val strikes = SkillStrikes(
    BattleContextFactory(PropPromotionService(mockk(relaxed = true))),
    AttackStrategyFactory(LineOfSightService(), FixedRandom(0.5f)),
    mockk(relaxed = true),
  )

  @Test
  fun `a blow with the second weapon uses its own ATK`() {
    val duelist = fighter(Vec3L(0, 0, 0), offHand = Weapon(atk = 200, matk = 0, upgradeLevel = 0))
    val target = fighter(Vec3L(1, 0, 0))
    val melee = BattleAttack.getBasicMeleeAttack()

    val mainHand = strikes.strike(world, duelist, target, melee, powerPercent = 100)!!.amount
    val offHand = strikes.strike(world, duelist, target, melee, powerPercent = 100, offHand = true)!!.amount

    assertTrue(offHand > mainHand, "the second weapon's 200 ATK hits harder than bare hands")
  }

  @Test
  fun `there is no blow with a second weapon nobody holds`() {
    val fighter = fighter(Vec3L(0, 0, 0))
    val target = fighter(Vec3L(1, 0, 0))

    assertNull(strikes.strike(world, fighter, target, BattleAttack.getBasicMeleeAttack(), 100, offHand = true))
  }

  private fun fighter(at: Vec3L, offHand: Weapon? = null): Long {
    return world.createEntity { id ->
      add(id, Position.fromVec3(at))
      val stats = BattleContextFixture.combatStats()
      stats.values = stats.values.copy(offHandWeapon = offHand)
      add(id, stats)
    }
  }
}
