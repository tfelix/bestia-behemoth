package net.bestia.zone.casting

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.ecs.damage.IncomingDamage
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.status.scripts.Reflect
import net.bestia.zone.boot.StatusEffectImporterBootRunner
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.util.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals

class ReflectTest {

  private val world = testWorld()
  private val statusEffects = StatusEffectService(
    StatusEffectDefinitionRegistry().also { StatusEffectImporterBootRunner(it).run() },
    StatusEffectScriptRegistry(listOf(Reflect()))
  )
  private val caster = body()
  private val target = body()

  @Test
  fun `a mirror turns a skill back on its caster`() {
    statusEffects.applyEffect(world, target, StatusEffectId.REFLECT, level = 1)

    skillWorld().apply(target, HitDamage(30))

    assertEquals(30, staged(caster))
    assertEquals(0, staged(target))
  }

  @Test
  fun `two mirrors bounce it back once, then it lands`() {
    statusEffects.applyEffect(world, target, StatusEffectId.REFLECT, level = 5)
    statusEffects.applyEffect(world, caster, StatusEffectId.REFLECT, level = 5)

    skillWorld().apply(target, HitDamage(30))

    assertEquals(30, staged(target))
    assertEquals(0, staged(caster))
  }

  private fun body(): EntityId {
    return world.createEntity { id -> add(id, Health(100, 100)) }
  }

  private fun staged(id: EntityId): Int {
    return world.get(id, IncomingDamage::class)?.total() ?: 0
  }

  private fun skillWorld(): BudgetedSkillWorld {
    val services = mockk<SkillWorldServices>(relaxed = true)
    every { services.statusEffects } returns statusEffects

    return BudgetedSkillWorld(world, SkillBudget(64, 32, 250L), services, casterId = caster, skillId = 1L, skillLevel = 1)
  }
}
