package net.bestia.zone.ecs.battle.effects

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.skill.SkillTargetType
import net.bestia.zone.battle.skill.SkillType
import net.bestia.zone.battle.skill.passive.PassiveSkillScript
import net.bestia.zone.battle.skill.passive.PassiveSkillScriptRegistry
import net.bestia.zone.battle.status.RegenModifier
import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectDefinition
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.status.ConditionValueCalculator
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.ecs.battle.status.BaseStatusValues
import net.bestia.zone.ecs.battle.status.FormulaDrivenVitals
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.status.IsStatusValueDirty
import net.bestia.zone.ecs.battle.status.RegenerationModifiers
import net.bestia.zone.ecs.battle.status.StatusValues
import net.bestia.zone.skill.Skill
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
    override fun apply(
      world: World,
      entityId: EntityId,
      context: StatusValueRecalcContext,
      level: Int,
      sourceEntityId: EntityId?
    ) {
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
    override fun apply(
      world: World,
      entityId: EntityId,
      context: StatusValueRecalcContext,
      level: Int,
      sourceEntityId: EntityId?
    ) {
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

  private val regenEffect = StatusEffectDefinition(
    id = 3L,
    identifier = "TEST_REGEN_BUFF",
    isSyncedToClient = true,
    script = "RegenBuffScript"
  )

  /** A stand-in [StatusEffectScript] granting a flat-and-percentage HP regeneration bonus. */
  private class RegenBuffScript(
    override val stackBehavior: StackBehavior = StackBehavior.REFRESH_DURATION,
    private val duration: Double = 1.0
  ) : StatusEffectScript {
    override fun durationSeconds(level: Int): Double = duration
    override fun apply(
      world: World,
      entityId: EntityId,
      context: StatusValueRecalcContext,
      level: Int,
      sourceEntityId: EntityId?
    ) {
      context.addHpRegen(percent = 30)
    }
  }

  /** A stand-in [EquipmentScript] granting a flat HP regeneration bonus. */
  private class RegenRingScript : EquipmentScript {
    override fun apply(context: StatusValueRecalcContext, slot: EquipmentSlot, upgradeLevel: Int) {
      context.addHpRegen(flat = 2)
    }
  }

  /** A stand-in [PassiveSkillScript] in the shape of the real `InnerPeace`. */
  private class TestPassiveScript : PassiveSkillScript {
    override val skillIdentifier = "TEST_PASSIVE"
    override fun apply(context: StatusValueRecalcContext, level: Int) {
      context.addHpRegen(percent = 20 * level)
    }
  }

  private val conditionValueCalculator = ConditionValueCalculator()

  private val passiveSkill = Skill(
    id = PASSIVE_SKILL_ID,
    identifier = "TEST_PASSIVE",
    strength = null,
    type = SkillType.PASSIVE,
    script = null,
    manaCost = 0,
    range = null,
    targetType = SkillTargetType.FRIENDLY,
    needsLineOfSight = false,
    requiredLevel = 0
  )

  private fun newWorld(
    script: StatusEffectScript,
    equipmentScriptRegistry: EquipmentScriptRegistry = EquipmentScriptRegistry(emptyList()),
    passiveSkillScriptRegistry: PassiveSkillScriptRegistry = PassiveSkillScriptRegistry(emptyList())
  ): Pair<World, StatusEffectDefinitionRegistry> {
    val definitionRegistry = StatusEffectDefinitionRegistry()
    definitionRegistry.load(listOf(speedEffect, vitalityEffect, regenEffect))

    val scriptRegistry = StatusEffectScriptRegistry(listOf(script))

    val world = testWorld(
      systems = listOf(
        StatusEffectDurationSystem(),
        StatusValueRecalcSystem(
          definitionRegistry,
          scriptRegistry,
          equipmentScriptRegistry,
          passiveSkillScriptRegistry,
          conditionValueCalculator
        )
      )
    )
    return world to definitionRegistry
  }

  /** A registry with [TestPassiveScript] already bound to [passiveSkill]. */
  private fun boundPassiveRegistry(): PassiveSkillScriptRegistry =
    PassiveSkillScriptRegistry(listOf(TestPassiveScript())).apply { bind(listOf(passiveSkill)) }

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
    // Opts this entity into formula-derived pool maxima, the way both player spawners do. Without
    // it the recalc leaves Health.max alone - see the mob test below.
    world.add(entity, FormulaDrivenVitals)

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
  fun `a passive, worn gear and a status effect all fold into one RegenerationModifiers`() {
    val ringItem = Item(
      id = 6L, identifier = "regen_ring", weight = 1, type = Item.ItemType.EQUIP,
      script = "RegenRingScript", equipSlot = EquipmentSlot.FOOTGEAR
    )
    val equipmentScriptRegistry = EquipmentScriptRegistry(listOf(RegenRingScript()))
    equipmentScriptRegistry.bind(listOf(ringItem))

    val (world, registry) = newWorld(RegenBuffScript(), equipmentScriptRegistry, boundPassiveRegistry())
    val entity = world.create()
    world.seedStatusValues(entity)
    world.add(entity, KnownSkills(mutableMapOf(PASSIVE_SKILL_ID to 2)))
    val equipment = Equipment(EquipmentSlots.ALL)
    world.add(entity, equipment)
    equipment.equip(EquipmentSlot.FOOTGEAR, Equipment.EquippedItem(itemId = ringItem.id, uniqueId = 1L))

    StatusEffectService(registry, StatusEffectScriptRegistry(listOf(RegenBuffScript())))
      .applyEffect(world, entity, definitionId = regenEffect.id, level = 1)

    world.tick(0.1f)

    // Passive (+20%/lv at level 2) and effect (+30%) sum additively; the ring's flat +2 is separate.
    assertEquals(
      RegenModifier(flat = 2, percent = 70),
      world.get(entity, RegenerationModifiers::class)!!.hp
    )
    // Pools nothing touched stay neutral rather than inheriting the HP bonus.
    assertEquals(RegenModifier(), world.get(entity, RegenerationModifiers::class)!!.mana)
  }

  @Test
  fun `RegenerationModifiers is rebuilt from scratch, so an expired buff stops contributing`() {
    // The CarryCapacity regression guard: this fails loudly if the write-back ever becomes `+=`
    // instead of an overwrite, or if the recalc starts from the previous result instead of base.
    val (world, registry) = newWorld(RegenBuffScript(duration = 1.0), passiveSkillScriptRegistry = boundPassiveRegistry())
    val entity = world.create()
    world.seedStatusValues(entity)
    world.add(entity, KnownSkills(mutableMapOf(PASSIVE_SKILL_ID to 1)))

    StatusEffectService(registry, StatusEffectScriptRegistry(listOf(RegenBuffScript(duration = 1.0))))
      .applyEffect(world, entity, definitionId = regenEffect.id, level = 1)

    world.tick(0.1f)
    assertEquals(RegenModifier(percent = 50), world.get(entity, RegenerationModifiers::class)!!.hp)

    // Recalculating without anything changing must not double the contributions.
    world.add(entity, IsStatusValueDirty)
    world.tick(0.1f)
    assertEquals(RegenModifier(percent = 50), world.get(entity, RegenerationModifiers::class)!!.hp)

    world.tick(1.0f) // fully expires the 1s duration
    world.tick(0.1f) // recalc picks up the dirty marker deferred by the expiry tick

    // Back to the passive alone - the expired effect's 30% is gone rather than baked in.
    assertEquals(RegenModifier(percent = 20), world.get(entity, RegenerationModifiers::class)!!.hp)
  }

  @Test
  fun `a mob without KnownSkills gets no passive contribution`() {
    val (world, _) = newWorld(SpeedBuffScript(), passiveSkillScriptRegistry = boundPassiveRegistry())
    val mob = world.create()
    world.seedStatusValues(mob)

    world.add(mob, IsStatusValueDirty)
    world.tick(0.1f)

    assertEquals(RegenModifier(), world.get(mob, RegenerationModifiers::class)!!.hp)
  }

  @Test
  fun `a mob without FormulaDrivenVitals keeps its authored max HP through a status effect`() {
    // A mob's pool is content, not formula: it comes from its species row (Bestia.health). Without
    // the marker gate the recalc recomputed it with the *player* formula, and since a mob carries no
    // Level component that means level 1 - so a 500 HP boss became an 18 HP one the first time
    // anything applied a status effect to it, permanently, because CurMax.max clamps current down
    // with it.
    val (world, registry) = newWorld(VitalityBuffScript(vitalityBonus = 20, duration = 1.0))
    val mob = world.create()
    world.seedStatusValues(mob)
    world.add(mob, Health(current = 500, max = 500))

    StatusEffectService(registry, StatusEffectScriptRegistry(listOf(VitalityBuffScript(duration = 1.0))))
      .applyEffect(world, mob, definitionId = vitalityEffect.id, level = 1)

    world.tick(0.1f)

    assertEquals(500, world.get(mob, Health::class)!!.max, "an authored pool must survive a recalc")
    assertEquals(500, world.get(mob, Health::class)!!.current)
    // The buff itself still lands - only the pool maximum is off limits, so debuffs and slows keep
    // working on mobs.
    assertEquals(30, world.get(mob, StatusValues::class)!!.vitality)
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

  private companion object {
    const val PASSIVE_SKILL_ID = 99L
  }
}
