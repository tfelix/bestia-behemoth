package net.bestia.zone.battle.ecs.status

import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationSuccessProto.OpSuccess
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.ecs.effects.StatusValueRecalcSystem
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.passive.PassiveSkillScriptRegistry
import net.bestia.zone.battle.status.CombatValuesPool
import net.bestia.zone.battle.status.ConditionValueCalculator
import net.bestia.zone.battle.status.RegenerationCalculator
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.ecs.itemTemplatesOf
import net.bestia.zone.item.equip.EquipmentStatusContributor
import net.bestia.zone.item.equip.script.EquipmentScriptRegistry
import net.bestia.zone.item.script.BreadScript
import net.bestia.zone.item.stat.ItemStatCatalog
import net.bestia.zone.message.OperationSuccessSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Speed
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [StaminaExhaustionSystem] against a real [EcsWorld], with the systems that read what it writes. */
class StaminaExhaustionSystemTest {

  private val messages = mockk<OutMessageProcessor>(relaxed = true)
  private val calculator = RegenerationCalculator()
  private val exhaustion = StaminaExhaustionSystem(StaminaConfig(), messages)

  private fun recalc(): StatusValueRecalcSystem = StatusValueRecalcSystem(
    StatusEffectDefinitionRegistry(),
    StatusEffectScriptRegistry(emptyList()),
    EquipmentStatusContributor(EquipmentScriptRegistry(emptyList()), ItemStatCatalog(), itemTemplatesOf()),
    PassiveSkillScriptRegistry(emptyList()),
    ConditionValueCalculator(),
    CombatValuesPool()
  )

  private fun newWorld(vararg others: System): EcsWorld = testWorld(systems = listOf(exhaustion) + others)

  private fun EcsWorld.master(stamina: Int, staminaMax: Int = 100, health: Int = 1_000, healthMax: Int = 1_000): EntityId {
    return createEntity { id ->
      add(id, Master(1L))
      add(id, Account(ACCOUNT))
      add(id, Level(1))
      add(id, BattleContextFixture.combatStats(strength = 9, vitality = 9, intelligence = 9, agility = 9, willpower = 9, dexterity = 9))
      add(id, Speed(4f))
      add(id, Stamina(stamina, staminaMax))
      add(id, Health(health, healthMax))
    }
  }

  /** Long enough for one more pass of the one-second schedule, and then for the recalc it asked for. */
  private fun EcsWorld.settle() {
    tick(1f)
    tick(0.05f)
  }

  private fun EcsWorld.seconds(n: Int) = repeat(n) { tick(1f) }

  @Test
  fun `walk speed falls linearly from 30 percent stamina to half at none`() {
    assertEquals(100, StaminaExhaustionSystem.speedPercentAt(100))
    assertEquals(100, StaminaExhaustionSystem.speedPercentAt(30))
    assertEquals(92, StaminaExhaustionSystem.speedPercentAt(29), "steps of 5 %, rounded down: 29 reads as 25")
    assertEquals(75, StaminaExhaustionSystem.speedPercentAt(15))
    assertEquals(58, StaminaExhaustionSystem.speedPercentAt(9))
    assertEquals(50, StaminaExhaustionSystem.speedPercentAt(0))
  }

  @Test
  fun `the recalc applies the speed of the stamina left`() {
    for ((stamina, expected) in listOf(30 to 4f, 15 to 3f, 0 to 2f)) {
      val world = newWorld(recalc())
      val master = world.master(stamina)

      world.settle()

      assertEquals(expected, world.get(master, Speed::class)!!.speed, 0.001f, "speed at $stamina % stamina")
    }
  }

  @Test
  fun `speed comes back once stamina is refilled`() {
    val world = newWorld(recalc())
    val master = world.master(0)
    world.settle()
    assertEquals(2f, world.get(master, Speed::class)!!.speed, 0.001f)

    world.get(master, Stamina::class)!!.current = 100
    world.settle()

    assertEquals(4f, world.get(master, Speed::class)!!.speed, 0.001f)
    assertNull(world.get(master, Exhaustion::class), "a rested entity carries no Exhaustion")
  }

  @Test
  fun `health and mana stop regenerating below 10 percent stamina`() {
    val world = newWorld(HpRegenSystem(calculator), ManaRegenSystem(calculator))
    val exhausted = world.master(stamina = 9, health = 1, healthMax = 18).also { world.add(it, Mana(1, 28)) }
    val tired = world.master(stamina = 15, health = 1, healthMax = 18).also { world.add(it, Mana(1, 28)) }

    world.tick(1f)
    world.tick(8.1f)

    assertEquals(1, world.get(exhausted, Health::class)!!.current, "no HP regen while exhausted")
    assertEquals(1, world.get(exhausted, Mana::class)!!.current, "no mana regen while exhausted")
    assertTrue(world.get(tired, Health::class)!!.current > 1, "a merely tired entity still regenerates HP")
    assertTrue(world.get(tired, Mana::class)!!.current > 1, "a merely tired entity still regenerates mana")
  }

  @Test
  fun `below 5 percent stamina the entity loses health every 30 seconds until it eats`() {
    val world = newWorld()
    val master = world.master(stamina = 4)

    world.tick(1f)
    world.seconds(29)
    assertEquals(1_000, world.get(master, Health::class)!!.current, "nothing before the first 30 s are up")

    world.seconds(1)
    assertEquals(997, world.get(master, Health::class)!!.current, "0.3 % of 1 000")

    world.seconds(30)
    assertEquals(994, world.get(master, Health::class)!!.current)

    assertTrue(BreadScript().restore(world, master, 100))
    world.seconds(60)

    assertEquals(994, world.get(master, Health::class)!!.current, "fed back above 5 %, the drain stops")
    assertNull(world.get(master, Exhaustion::class))
  }

  @Test
  fun `the drain can kill`() {
    val world = newWorld()
    val master = world.master(stamina = 0, health = 1, healthMax = 100)

    world.seconds(31)

    assertEquals(0, world.get(master, Health::class)!!.current)
    assertTrue(world.has(master, Dead::class))
  }

  @Test
  fun `a wild mob is untouched`() {
    val world = newWorld(recalc())
    val mob = world.createEntity { id ->
      add(id, Level(1))
      add(id, BattleContextFixture.combatStats())
      add(id, Speed(4f))
      add(id, Stamina(0, 10))
      add(id, Health(100, 100))
    }

    world.seconds(61)

    assertNull(world.get(mob, Exhaustion::class))
    assertEquals(4f, world.get(mob, Speed::class)!!.speed, 0.001f)
    assertEquals(100, world.get(mob, Health::class)!!.current)
  }

  @Test
  fun `an owned bestia is worn down too, and its master told under its own wording`() {
    val world = newWorld()
    val bestia = world.createEntity { id ->
      add(id, OwnedBestia(masterId = 1L, playerBestiaId = 7L))
      add(id, Account(ACCOUNT))
      add(id, Stamina(20, 100))
      add(id, Health(100, 100))
    }

    world.seconds(3)

    assertEquals(StaminaBand.TIRED, world.get(bestia, Exhaustion::class)!!.band)
    verify(exactly = 1) { messages.sendToPlayer(ACCOUNT, OperationSuccessSMSG(OpSuccess.BESTIA_STAMINA_TIRED)) }
  }

  @Test
  fun `each band is announced once on the way down, and again after recovering`() {
    val world = newWorld()
    val master = world.master(stamina = 25)
    val stamina = world.get(master, Stamina::class)!!

    world.seconds(3)
    stamina.current = 8
    world.seconds(3)
    stamina.current = 3
    world.seconds(3)
    // Climbing back through a band is not news.
    stamina.current = 8
    world.seconds(3)

    verify(exactly = 1) { messages.sendToPlayer(ACCOUNT, OperationSuccessSMSG(OpSuccess.STAMINA_TIRED)) }
    verify(exactly = 1) { messages.sendToPlayer(ACCOUNT, OperationSuccessSMSG(OpSuccess.STAMINA_EXHAUSTED)) }
    verify(exactly = 1) { messages.sendToPlayer(ACCOUNT, OperationSuccessSMSG(OpSuccess.STAMINA_COLLAPSING)) }

    stamina.current = 50
    world.seconds(2)
    assertFalse(world.has(master, Exhaustion::class))
    stamina.current = 20
    world.seconds(2)

    verify(exactly = 2) { messages.sendToPlayer(ACCOUNT, OperationSuccessSMSG(OpSuccess.STAMINA_TIRED)) }
  }

  private companion object {
    const val ACCOUNT = 42L
  }
}
