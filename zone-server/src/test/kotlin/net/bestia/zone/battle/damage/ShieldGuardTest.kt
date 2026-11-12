package net.bestia.zone.battle.damage

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.FixedRandom
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.attack.AttackStrategyFactory
import net.bestia.zone.battle.attack.BattleAttack
import net.bestia.zone.battle.BattleContextFactory
import net.bestia.zone.battle.ecs.damage.IncomingDamage
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.casting.BudgetedSkillWorld
import net.bestia.zone.casting.SkillBudget
import net.bestia.zone.casting.SkillStrikes
import net.bestia.zone.casting.SkillWorldServices
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PropPromotionService
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ShieldGuardTest {

  private val world = testWorld()
  private val blow = HitKind.physical(HitReach.MELEE)

  /** Draws 0, so any block chance at all catches the blow. */
  private val alwaysBlocks = ShieldGuard(FixedRandom(0f))

  @Test
  fun `a shield catches a weapon blow at its block chance`() {
    val knight = fighter(blockChance = 20)

    assertEquals(Blocked, alwaysBlocks.guard(world, knight, blow, HitDamage(40)))
    assertEquals(Blocked, alwaysBlocks.guard(world, knight, blow, CriticalHit(80)))
    assertEquals(HitDamage(40), ShieldGuard(FixedRandom(0.2f)).guard(world, knight, blow, HitDamage(40)))
  }

  @Test
  fun `a caught blow is told to the effects on the one who caught it`() {
    val statusEffects = mockk<StatusEffectService>(relaxed = true)
    val knight = fighter(blockChance = 100)

    ShieldGuard(FixedRandom(0f), statusEffects).guard(world, knight, blow, HitDamage(40))
    ShieldGuard(FixedRandom(0f), statusEffects).guard(world, knight, HitKind.magic(), HitDamage(40))

    verify(exactly = 1) { statusEffects.notifyBlocked(world, knight) }
  }

  @Test
  fun `a spell, a miss and a heal pass the shield`() {
    val knight = fighter(blockChance = 100)

    assertEquals(HitDamage(40), alwaysBlocks.guard(world, knight, HitKind.magic(), HitDamage(40)))
    assertEquals(Miss, alwaysBlocks.guard(world, knight, blow, Miss))
    assertEquals(Heal(40), alwaysBlocks.guard(world, knight, blow, Heal(40)))
  }

  @Test
  fun `without a block chance nothing is caught`() {
    assertEquals(HitDamage(40), alwaysBlocks.guard(world, fighter(blockChance = 0), blow, HitDamage(40)))
  }

  @Test
  fun `a skill's weapon blow the shield catches stages nothing and comes back as a block`() {
    val caster = fighter(blockChance = 0)
    val knight = fighter(blockChance = 100)
    val services = mockk<SkillWorldServices>(relaxed = true)
    every { services.shieldGuard } returns alwaysBlocks
    every { services.strikes } returns SkillStrikes(
      BattleContextFactory(PropPromotionService(mockk(relaxed = true))),
      AttackStrategyFactory(LineOfSightService(), FixedRandom(0f)),
      mockk(relaxed = true),
    )
    val skillWorld = BudgetedSkillWorld(world, SkillBudget(64, 32, 250L), services, casterId = caster, skillId = 1L, skillLevel = 1)

    assertEquals(Blocked, skillWorld.strike(knight, BattleAttack.getBasicMeleeAttack(), powerPercent = 100))
    assertFalse(world.has(knight, IncomingDamage::class))
  }

  private fun fighter(blockChance: Int): EntityId {
    return world.createEntity { id ->
      add(id, Position.fromVec3(Vec3L(id, 0, 0)))
      add(id, Health(100, 100))
      add(id, CombatStats(BattleContextFixture.combatStats().values.copy(blockChancePercent = blockChance)))
    }
  }
}
