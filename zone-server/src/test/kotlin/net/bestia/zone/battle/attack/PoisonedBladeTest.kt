package net.bestia.zone.battle.attack

import io.mockk.mockk
import net.bestia.zone.battle.FixedRandom
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.passive.scripts.PoisonResearch
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.battle.status.scripts.Poison
import net.bestia.zone.battle.status.scripts.PoisonedWeapon
import net.bestia.zone.boot.StatusEffectImporterBootRunner
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.skillIdsOf
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PoisonedBladeTest {

  private val definitions = StatusEffectDefinitionRegistry().also { StatusEffectImporterBootRunner(it).run() }
  private val scripts = StatusEffectScriptRegistry(listOf(Poison(), PoisonedWeapon()))
  private val world = testWorld()
  private val skillIds = skillIdsOf(SkillId.M_POISON_RESEARCH to POISON_RESEARCH_ID)

  @Test
  fun `a poisoned blade poisons what it lands on, harder in the hands of a researcher`() {
    val assassin = fighter(KnownSkills(mutableMapOf(POISON_RESEARCH_ID to 5)))
    val target = fighter()
    val service = StatusEffectService(definitions, scripts, FixedRandom(0f))
    service.applyEffect(world, assassin, StatusEffectId.ENCHANT_POISON, level = 1, amount = 10)

    PoisonedBlade(service, skillIds).onSwingLanded(world, assassin, target, HitDamage(5))

    val poison = world.getOrThrow(target, StatusEffects::class).activeEffects.single { it.definitionId == StatusEffectId.POISON.id }
    assertEquals(20, poison.amount, "Poison Research 5 doubles the drain")
    assertEquals(assassin, poison.sourceEntityId)
  }

  @Test
  fun `the poison lands 5 percent of the time a level`() {
    val assassin = fighter()
    val target = fighter()
    val service = StatusEffectService(definitions, scripts, FixedRandom(0.06f))
    service.applyEffect(world, assassin, StatusEffectId.ENCHANT_POISON, level = 1, amount = 10)

    PoisonedBlade(service, skillIds).onSwingLanded(world, assassin, target, HitDamage(5))

    assertFalse(world.get(target, StatusEffects::class)?.hasEffect(StatusEffectId.POISON.id) == true)
    assertEquals(listOf(5, 10, 15, 20, 25), (1..5).map { PoisonedWeapon.chancePercent(it) })
  }

  @Test
  fun `Poison Research resists poison 10 percent a level`() {
    val context = StatusValueRecalcContext(BASE, baseSpeed = 1f)

    PoisonResearch().apply(context, 3)

    assertEquals(30, CombatValues.fromContext(1, BASE, context).statusResistance[StatusEffectId.POISON])
    assertEquals(listOf(12, 14, 16, 18, 20), (1..5).map { PoisonResearch.researchedDrain(10, it) })
  }

  @Test
  fun `a bare blade poisons nothing`() {
    val target = fighter()
    val service = StatusEffectService(definitions, scripts, FixedRandom(0f))

    PoisonedBlade(service, skillIds).onSwingLanded(world, fighter(), target, HitDamage(5))

    assertTrue(world.get(target, StatusEffects::class)?.activeEffects.isNullOrEmpty())
  }

  private fun fighter(known: KnownSkills? = null): EntityId {
    return world.createEntity { id ->
      add(id, CombatStats(CombatValues.ofBase(1, BASE)))
      known?.let { add(id, it) }
    }
  }

  private companion object {
    const val POISON_RESEARCH_ID = 124L
    val BASE = StatusValues(strength = 1, vitality = 1, intelligence = 1, agility = 1, willpower = 1, dexterity = 1)
  }
}
