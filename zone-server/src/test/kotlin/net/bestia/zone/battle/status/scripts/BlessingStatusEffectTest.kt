package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.Size
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffectDurationSystem
import net.bestia.zone.battle.ecs.effects.StatusValueRecalcSystem
import net.bestia.zone.battle.ecs.status.BaseStatusValues
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.battle.ecs.status.StatusValues
import net.bestia.zone.battle.passive.PassiveSkillScriptRegistry
import net.bestia.zone.battle.status.CombatValuesPool
import net.bestia.zone.battle.status.ConditionValueCalculator
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.boot.StatusEffectImporterBootRunner
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.item.equip.EquipmentStatusContributor
import net.bestia.zone.item.equip.script.EquipmentScriptRegistry
import net.bestia.zone.util.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Blessing against the shipped catalogue, driven by the real recalc system - which, in a test world, also fails
 * the run if the system reads a component it did not declare.
 */
class BlessingStatusEffectTest {

  private val blessing = BlessingStatusEffect()
  private val catalogue = StatusEffectDefinitionRegistry().also { StatusEffectImporterBootRunner(it).run() }
  private val scripts = StatusEffectScriptRegistry(listOf(blessing))
  private val statusEffects = StatusEffectService(catalogue, scripts)
  private val world = testWorld(
    systems = listOf(
      StatusEffectDurationSystem(statusEffects, catalogue, scripts),
      StatusValueRecalcSystem(
        catalogue,
        scripts,
        EquipmentStatusContributor(EquipmentScriptRegistry(emptyList())),
        PassiveSkillScriptRegistry(emptyList()),
        ConditionValueCalculator(),
        CombatValuesPool()
      )
    )
  )

  @Test
  fun `blessing adds its level to strength, dexterity and intelligence`() {
    val target = creature()

    bless(target, level = 4)

    assertEquals(StatusValues(strength = 24, intelligence = 34, vitality = 10, dexterity = 44, willpower = 50, agility = 60), statsOf(target))
  }

  @Test
  fun `a creature of another element is blessed like anyone else`() {
    val target = creature(Nature(Element.FIRE_3, Size.MEDIUM))

    bless(target, level = 4)

    assertEquals(24, statsOf(target).strength)
    assertEquals(44, statsOf(target).dexterity)
  }

  @Test
  fun `an undead target has its dexterity and intelligence halved and its strength left alone`() {
    val target = creature(Nature(Element.UNDEAD, Size.MEDIUM))

    bless(target, level = 10)

    assertEquals(StatusValues(strength = 20, intelligence = 15, vitality = 10, dexterity = 20, willpower = 50, agility = 60), statsOf(target))
  }

  @Test
  fun `every level of the undead element is cursed`() {
    for (element in listOf(Element.UNDEAD_2, Element.UNDEAD_3, Element.UNDEAD_4)) {
      val target = creature(Nature(element, Size.MEDIUM))

      bless(target, level = 1)

      assertEquals(20, statsOf(target).dexterity, "$element")
    }
  }

  @Test
  fun `the blessing lasts longer with every level`() {
    assertEquals(60.0, blessing.durationSeconds(1))
    assertEquals(140.0, blessing.durationSeconds(5))
    assertEquals(240.0, blessing.durationSeconds(10))
  }

  private fun creature(nature: Nature? = null): EntityId {
    val values = BaseStatusValues(strength = 20, intelligence = 30, vitality = 10, dexterity = 40, willpower = 50, agility = 60)

    return world.createEntity { id ->
      add(id, values)
      add(id, StatusValues(values.strength, values.intelligence, values.vitality, values.dexterity, values.willpower, values.agility))
      nature?.let { add(id, it) }
    }
  }

  private fun bless(target: EntityId, level: Int) {
    statusEffects.applyEffect(world, target, StatusEffectId.BLESSING, level)
    world.tick(0.1f)
  }

  private fun statsOf(target: EntityId): StatusValues = world.get(target, StatusValues::class)!!
}
