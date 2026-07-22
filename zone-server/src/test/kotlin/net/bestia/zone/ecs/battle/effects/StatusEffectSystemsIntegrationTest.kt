package net.bestia.zone.ecs.battle.effects

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectDefinition
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.status.ConditionValueCalculator
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.battle.status.BaseStatusValues
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.status.IsStatusValueDirty
import net.bestia.zone.ecs.battle.status.StatusValues
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.item.Equipment
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.item.Item
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.EquipmentSlots
import net.bestia.zone.item.equip.script.EquipmentScript
import net.bestia.zone.item.equip.script.EquipmentScriptRegistry
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises [StatusEffectDurationSystem] and [StatusValueRecalcSystem] wired together against a
 * real [World], the same way [net.bestia.zone.ecs.EcsConfiguration] wires them in production
 * (minus Spring) - to verify the cross-system, recalc-from-scratch behavior, not just each
 * component in isolation.
 */
class StatusEffectSystemsIntegrationTest {

  /** A stand-in [StatusEffectScript] for a flat speed multiplier, registered by simple class name. */
  private class SpeedBuffScript(
    override val stackBehavior: StackBehavior = StackBehavior.REFRESH_DURATION,
    private val speedMultiplier: Float = 1.5f,
    private val duration: Double = 1.0
  ) : StatusEffectScript {
    override fun durationSeconds(level: Int): Double = duration
    override fun apply(context: StatusValueRecalcContext, level: Int, sourceEntityId: EntityId?) {
      context.speed *= speedMultiplier
    }
  }

  /** A stand-in [EquipmentScript] for a flat agility bonus, registered by simple class name. */
  private class AgilityBootsScript(
    private val agilityBonus: Int = 5
  ) : EquipmentScript {
    override fun apply(context: StatusValueRecalcContext, slot: EquipmentSlot, upgradeLevel: Int) {
      context.agility += agilityBonus
    }
  }

  /** A stand-in [StatusEffectScript] for a flat vitality bonus, registered by simple class name. */
  private class VitalityBuffScript(
    override val stackBehavior: StackBehavior = StackBehavior.REFRESH_DURATION,
    private val vitalityBonus: Int = 20,
    private val duration: Double = 1.0
  ) : StatusEffectScript {
    override fun durationSeconds(level: Int): Double = duration
    override fun apply(context: StatusValueRecalcContext, level: Int, sourceEntityId: EntityId?) {
      context.vitality += vitalityBonus
    }
  }

  private val speedEffect = StatusEffectDefinition(
    id = 1L,
    identifier = "TEST_SPEED_BUFF",
    isSyncedToClient = true,
    script = "SpeedBuffScript"
  )

  private val vitalityEffect = StatusEffectDefinition(
    id = 2L,
    identifier = "TEST_VIT_BUFF",
    isSyncedToClient = true,
    script = "VitalityBuffScript"
  )

  private val conditionValueCalculator = ConditionValueCalculator()

  private fun newWorld(
    script: StatusEffectScript,
    equipmentScriptRegistry: EquipmentScriptRegistry = EquipmentScriptRegistry(emptyList())
  ): Pair<World, StatusEffectDefinitionRegistry> {
    val definitionRegistry = StatusEffectDefinitionRegistry()
    definitionRegistry.load(listOf(speedEffect, vitalityEffect))

    val scriptRegistry = StatusEffectScriptRegistry(listOf(script))

    val world = testWorld(
      systems = listOf(
        StatusEffectDurationSystem(),
        StatusValueRecalcSystem(definitionRegistry, scriptRegistry, equipmentScriptRegistry, conditionValueCalculator)
      )
    )
    return world to definitionRegistry
  }

  private fun World.seedStatusValues(entity: EntityId) {
    add(entity, BaseStatusValues(strength = 10, intelligence = 10, vitality = 10, dexterity = 10, willpower = 10, agility = 10))
    add(entity, StatusValues(strength = 10, intelligence = 10, vitality = 10, dexterity = 10, willpower = 10, agility = 10))
  }

  @Test
  fun `a speed effect raises effective speed and reverts once it expires`() {
    val (world, registry) = newWorld(SpeedBuffScript(speedMultiplier = 1.5f, duration = 1.0))
    val entity = world.create()
    world.add(entity, Speed(2.0f))
    world.seedStatusValues(entity)

    StatusEffectService(registry, StatusEffectScriptRegistry(listOf(SpeedBuffScript(duration = 1.0))))
      .applyEffect(world, entity, definitionId = speedEffect.id, level = 1)

    world.tick(0.1f)
    assertEquals(3.0f, world.get(entity, Speed::class)!!.speed, 0.001f)

    world.tick(1.0f) // fully expires the 1s duration
    assertTrue(world.get(entity, StatusEffects::class)!!.activeEffects.isEmpty())

    world.tick(0.1f) // recalc picks up the dirty marker deferred by the expiry tick
    assertEquals(2.0f, world.get(entity, Speed::class)!!.speed, 0.001f)
  }

  @Test
  fun `a vitality buff raises formula-driven max HP and reverts once it expires`() {
    val (world, registry) = newWorld(VitalityBuffScript(vitalityBonus = 20, duration = 1.0))
    val entity = world.create()
    world.seedStatusValues(entity)

    val baseMaxHp = conditionValueCalculator.computeMaxHp(level = 1, vitality = 10)
    val buffedMaxHp = conditionValueCalculator.computeMaxHp(level = 1, vitality = 30)
    world.add(entity, Health(current = baseMaxHp, max = baseMaxHp))
    // Guard: the buff must actually move the number, otherwise the assertions below prove nothing.
    assertTrue(buffedMaxHp > baseMaxHp)

    StatusEffectService(registry, StatusEffectScriptRegistry(listOf(VitalityBuffScript(duration = 1.0))))
      .applyEffect(world, entity, definitionId = vitalityEffect.id, level = 1)

    world.tick(0.1f)
    assertEquals(buffedMaxHp, world.get(entity, Health::class)!!.max)

    world.tick(1.0f) // fully expires the 1s duration
    world.tick(0.1f) // recalc picks up the dirty marker deferred by the expiry tick
    assertEquals(baseMaxHp, world.get(entity, Health::class)!!.max)
  }

  @Test
  fun `worn equipment raises effective status values and reverts when taken off`() {
    val bootsItem = Item(
      id = 4L, identifier = "boots", weight = 8, type = Item.ItemType.EQUIP,
      script = "AgilityBootsScript", equipSlot = EquipmentSlot.FOOTGEAR
    )
    val equipmentScriptRegistry = EquipmentScriptRegistry(listOf(AgilityBootsScript()))
    equipmentScriptRegistry.bind(listOf(bootsItem))

    val (world, _) = newWorld(SpeedBuffScript(), equipmentScriptRegistry)
    val entity = world.create()
    world.seedStatusValues(entity)
    val equipment = Equipment(EquipmentSlots.ALL)
    world.add(entity, equipment)

    equipment.equip(EquipmentSlot.FOOTGEAR, Equipment.EquippedItem(itemId = bootsItem.id, uniqueId = 1L))
    world.add(entity, IsStatusValueDirty)
    world.tick(0.1f)
    assertEquals(15, world.get(entity, StatusValues::class)!!.agility)

    equipment.unequip(EquipmentSlot.FOOTGEAR)
    world.add(entity, IsStatusValueDirty)
    world.tick(0.1f)
    assertEquals(10, world.get(entity, StatusValues::class)!!.agility)
  }

  @Test
  fun `a status effect stacks on top of the equipment bonus rather than replacing it`() {
    val bootsItem = Item(
      id = 4L, identifier = "boots", weight = 8, type = Item.ItemType.EQUIP,
      script = "AgilityBootsScript", equipSlot = EquipmentSlot.FOOTGEAR
    )
    val equipmentScriptRegistry = EquipmentScriptRegistry(listOf(AgilityBootsScript()))
    equipmentScriptRegistry.bind(listOf(bootsItem))

    val (world, registry) = newWorld(VitalityBuffScript(vitalityBonus = 20), equipmentScriptRegistry)
    val entity = world.create()
    world.seedStatusValues(entity)
    val equipment = Equipment(EquipmentSlots.ALL)
    world.add(entity, equipment)
    equipment.equip(EquipmentSlot.FOOTGEAR, Equipment.EquippedItem(itemId = bootsItem.id, uniqueId = 1L))

    StatusEffectService(registry, StatusEffectScriptRegistry(listOf(VitalityBuffScript(vitalityBonus = 20))))
      .applyEffect(world, entity, definitionId = vitalityEffect.id, level = 1)

    world.tick(0.1f)
    val values = world.get(entity, StatusValues::class)!!
    assertEquals(15, values.agility) // from the boots
    assertEquals(30, values.vitality) // from the buff
  }

  @Test
  fun `re-applying a REFRESH_DURATION effect resets its remaining time instead of stacking`() {
    val (world, registry) = newWorld(SpeedBuffScript(duration = 1.0))
    val entity = world.create()
    world.add(entity, Speed(2.0f))
    world.seedStatusValues(entity)
    val service = StatusEffectService(registry, StatusEffectScriptRegistry(listOf(SpeedBuffScript(duration = 1.0))))

    service.applyEffect(world, entity, definitionId = speedEffect.id, level = 1)
    world.tick(0.5f)
    service.applyEffect(world, entity, definitionId = speedEffect.id, level = 1)

    assertEquals(1, world.get(entity, StatusEffects::class)!!.activeEffects.size)
    assertEquals(1.0f, world.get(entity, StatusEffects::class)!!.activeEffects.single().remainingSeconds, 0.001f)
  }
}
