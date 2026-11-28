package net.bestia.zone.battle.ecs.status

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectDefinition
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectPolarity
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.status.scripts.WornOut
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Work is paid in stamina, by the hour of labour, and leaves the worker worn out. */
class WorkStrainSystemTest {

  private val config = StaminaConfig()
  private val wornOut = StatusEffectDefinition(
    id = StatusEffectId.WORN_OUT.id, identifier = "WORN_OUT", isSyncedToClient = true, script = "WornOut",
    polarity = StatusEffectPolarity.DEBUFF
  )
  private val statusEffects = StatusEffectService(
    StatusEffectDefinitionRegistry().apply { load(listOf(wornOut)) }, StatusEffectScriptRegistry(listOf(WornOut(config)))
  )
  private val labour = LabourLedger()
  private val world: EcsWorld = testWorld(systems = listOf(WorkStrainSystem(config, statusEffects, labour)))

  @Test
  fun `an hour of labour costs an hour's stamina and wears the worker out`() {
    val worker = master()

    labour.record(worker, 3600f)
    world.tick(STRAIN_DUE)

    assertEquals(100 - config.staminaPerLabourHour.toInt(), staminaOf(worker))
    assertTrue(world.get(worker, StatusEffects::class)!!.hasEffect(StatusEffectId.WORN_OUT.id))
  }

  @Test
  fun `small units of work add up rather than costing nothing`() {
    val worker = master()

    labour.record(worker, 60f)
    world.tick(STRAIN_DUE)
    assertEquals(100, staminaOf(worker), "a minute of labour is less than one point")

    labour.record(worker, 60f)
    world.tick(STRAIN_DUE)
    assertEquals(99, staminaOf(worker), "two minutes together are more than one")
  }

  @Test
  fun `a wild creature works for free`() {
    val creature = world.createEntity { }
    world.add(creature, Stamina(current = 10, max = 10))

    labour.record(creature, 3600f)
    world.tick(STRAIN_DUE)

    assertNull(world.get(creature, WorkStrain::class))
    assertEquals(10, staminaOf(creature))
    assertFalse(world.has(creature, StatusEffects::class))
  }

  private fun master(): EntityId {
    val id = world.createEntity { }
    world.add(id, Master(1L))
    world.add(id, Stamina(current = 100, max = 100))
    return id
  }

  private fun staminaOf(id: EntityId): Int {
    return world.get(id, Stamina::class)!!.current
  }

  private companion object {
    const val STRAIN_DUE = 1.1f
  }
}
