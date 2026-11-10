package net.bestia.zone.battle.ecs.effects

import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.BaseStatusValues
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.passive.PassiveSkillScript
import net.bestia.zone.battle.passive.PassiveSkillScriptRegistry
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.CombatValuesPool
import net.bestia.zone.battle.status.ConditionValueCalculator
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.ecs.itemTemplatesOf
import net.bestia.zone.item.equip.EquipmentStatusContributor
import net.bestia.zone.item.equip.script.EquipmentScriptRegistry
import net.bestia.zone.item.stat.ItemStatCatalog
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.SkillTargetType
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.persistence.Skill
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** A master's passive that reaches bestias works on them at the master's level. */
class OwnerPassiveReachTest {

  private class TeachingPassive(override val reachesOwnedBestias: Boolean) : PassiveSkillScript {
    override val skill = SkillId.M_MEDITATION

    override fun apply(context: StatusValueRecalcContext, level: Int) {
      context.addAttack(atk = BONUS_PER_LEVEL * level)
    }
  }

  private val meditation = Skill(
    id = SKILL_ID, identifier = "M_MEDITATION", strength = null, script = null, manaCost = 0, range = null,
    targetType = SkillTargetType.FRIENDLY, needsLineOfSight = false, requiredLevel = 0
  )

  private fun worldWith(passive: PassiveSkillScript) = testWorld(
    systems = listOf(
      StatusValueRecalcSystem(
        StatusEffectDefinitionRegistry(),
        StatusEffectScriptRegistry(emptyList()),
        EquipmentStatusContributor(EquipmentScriptRegistry(emptyList()), ItemStatCatalog(), itemTemplatesOf()),
        PassiveSkillScriptRegistry(listOf(passive)).apply { bind(listOf(meditation)) { false } },
        ConditionValueCalculator(),
        CombatValuesPool()
      )
    )
  )

  @Test
  fun `a bestia gets a reaching passive at its master's level`() {
    assertEquals(3 * BONUS_PER_LEVEL, recalculatedBestiaWeaponAtk(TeachingPassive(reachesOwnedBestias = true)))
  }

  @Test
  fun `a passive for the master alone does not reach the bestia`() {
    assertEquals(0, recalculatedBestiaWeaponAtk(TeachingPassive(reachesOwnedBestias = false)))
  }

  private fun recalculatedBestiaWeaponAtk(passive: PassiveSkillScript): Int {
    val world = worldWith(passive)
    world.createEntity { id ->
      add(id, Master(MASTER_ID))
      add(id, KnownSkills(mutableMapOf(SKILL_ID to 3)))
    }
    val bestia: EntityId = world.createEntity { id ->
      val base = BaseStatusValues(strength = 5, intelligence = 5, vitality = 5, dexterity = 5, willpower = 5, agility = 5)
      add(id, OwnedBestia(MASTER_ID, playerBestiaId = 9L))
      add(id, Level(10))
      add(id, base)
      add(id, CombatStats(CombatValues.ofBase(10, base.toAttributes())))
      add(id, IsStatusValueDirty)
    }

    world.tick(0.1f)

    return world.get(bestia, CombatStats::class)!!.values.weapon.atk
  }

  private companion object {
    const val SKILL_ID = 27L
    const val MASTER_ID = 4L
    const val BONUS_PER_LEVEL = 5
  }
}
