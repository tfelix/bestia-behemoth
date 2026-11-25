package net.bestia.zone.casting.essence

import io.mockk.mockk
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.status.scripts.NineTailsCount
import net.bestia.zone.battle.status.scripts.NineTailsReady
import net.bestia.zone.boot.StatusEffectImporterBootRunner
import net.bestia.zone.casting.SkillLevelCost
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.skill.SkillArchetype
import net.bestia.zone.skill.SkillTargetType
import net.bestia.zone.skill.persistence.Skill
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EssenceSkillCostTest {

  private val world = testWorld()
  private val statusEffects = StatusEffectService(
    StatusEffectDefinitionRegistry().also { StatusEffectImporterBootRunner(it).run() },
    StatusEffectScriptRegistry(listOf(NineTailsCount(), NineTailsReady()))
  )
  private val cost = SkillLevelCost(manaCost = 40, staminaCost = 0, cooldownSeconds = 0f, castTimeSeconds = 2f, rangeTiles = 5)

  @Test
  fun `a pharaoh's crown takes a quarter off any skill's mana`() {
    val caster = wearing(CombatValues.OBJECT_BASELINE.copy(manaCostCutPercent = 25))

    assertEquals(30, EssenceSkillCost.of(world, caster, skill(SkillArchetype.STRIKE), cost).manaCost)
  }

  @Test
  fun `nine tails makes the ninth spell instant and free, then counts again`() {
    val caster = wearing(CombatValues.OBJECT_BASELINE.copy(everyNinthSpellFree = true))
    val spell = skill(archetype = null)

    repeat(8) {
      assertEquals(cost, EssenceSkillCost.of(world, caster, spell, cost))
      NineTails.counted(world, caster, statusEffects)
    }

    val ninth = EssenceSkillCost.of(world, caster, spell, cost)
    assertEquals(0, ninth.manaCost)
    assertEquals(0f, ninth.castTimeSeconds)

    NineTails.counted(world, caster, statusEffects)
    assertEquals(cost, EssenceSkillCost.of(world, caster, spell, cost))
  }

  private fun wearing(values: CombatValues): EntityId = world.createEntity { id -> add(id, CombatStats(values)) }

  private fun skill(archetype: SkillArchetype?) = Skill(
    id = 1L, identifier = "TEST", strength = null, script = "Test", manaCost = 40, range = 5,
    targetType = SkillTargetType.ENEMY, needsLineOfSight = false, requiredLevel = 0, cooldown = 0f, archetype = archetype,
    element = "NORMAL"
  )
}
