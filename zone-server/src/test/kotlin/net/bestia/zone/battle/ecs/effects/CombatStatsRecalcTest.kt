package net.bestia.zone.battle.ecs.effects

import net.bestia.zone.item.ecs.itemTemplatesOf
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.ecs.status.BaseStatusValues
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.FormulaDrivenVitals
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.ecs.status.OwnerCombatStats
import net.bestia.zone.battle.ecs.status.RefillVitals
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.battle.ecs.status.StatusValues
import net.bestia.zone.battle.passive.PassiveSkillScriptRegistry
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.CombatValuesPool
import net.bestia.zone.battle.status.ConditionValueCalculator
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.item.equip.EquipmentStatusContributor
import net.bestia.zone.item.equip.script.EquipmentScriptRegistry
import net.bestia.zone.item.stat.ItemStatCatalog
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import net.bestia.zone.battle.status.StatusValues as Attributes

/**
 * [StatusValueRecalcSystem] resolving [CombatStats] for a mob and for an owner, against a real [EcsWorld].
 */
class CombatStatsRecalcTest {

  private val pool = CombatValuesPool()

  private val world: EcsWorld = testWorld(
    systems = listOf(
      StatusValueRecalcSystem(
        StatusEffectDefinitionRegistry(),
        StatusEffectScriptRegistry(emptyList()),
        EquipmentStatusContributor(EquipmentScriptRegistry(emptyList()), ItemStatCatalog(), itemTemplatesOf()),
        PassiveSkillScriptRegistry(emptyList()),
        ConditionValueCalculator(),
        pool
      )
    )
  )

  private val attributes = Attributes(
    strength = 6, vitality = 9, intelligence = 8, agility = 6, willpower = 13, dexterity = 10
  )

  private fun spawnMob(level: Int = 20): EntityId = world.createEntity { id ->
    add(id, Level(level))
    add(id, CombatStats(pool.share(CombatValues.ofBase(level, attributes))))
  }

  private fun spawnOwner(level: Int = 20): EntityId = world.createEntity { id ->
    val base = BaseStatusValues(
      strength = 6, intelligence = 8, vitality = 9, dexterity = 10, willpower = 13, agility = 6
    )
    val values = CombatValues.ofBase(level, base.toAttributes())

    add(id, Account(1L))
    add(id, Level(level))
    add(id, base)
    add(id, StatusValues(strength = 6, intelligence = 8, vitality = 9, dexterity = 10, willpower = 13, agility = 6))
    add(id, CombatStats(values))
    add(id, OwnerCombatStats(values))
  }

  private fun spawnOwnerWithVitals(): EntityId = spawnOwner().also { id ->
    world.add(id, FormulaDrivenVitals)
    world.add(id, Health(10, 50))
    world.add(id, Mana(5, 30))
  }

  private fun recalc(id: EntityId) {
    world.add(id, IsStatusValueDirty)
    world.tick(0.1f)
  }

  private fun CombatStats.atk() = values.derived.atk

  @Test
  fun `a recalc that changes nothing leaves a mob on the shared block of its species`() {
    val recalculated = spawnMob()
    val sibling = spawnMob()

    recalc(recalculated)

    assertSame(world.get(sibling, CombatStats::class)!!.values, world.get(recalculated, CombatStats::class)!!.values)
    assertEquals(1, pool.size)
  }

  @Test
  fun `a mob is never given the copies an owner is sent`() {
    val mob = spawnMob()

    recalc(mob)

    assertNull(world.get(mob, StatusValues::class))
    assertNull(world.get(mob, BaseStatusValues::class))
    assertNull(world.get(mob, OwnerCombatStats::class))
  }

  @Test
  fun `an owner's block is its own and never pooled`() {
    val owner = spawnOwner()

    recalc(owner)

    assertEquals(0, pool.size)
    assertEquals(CombatValues.ofBase(20, attributes), world.get(owner, CombatStats::class)!!.values)
  }

  @Test
  fun `a level up reaches combat and the owner's window in the same recalc`() {
    val owner = spawnOwner(level = 20)
    val before = world.get(owner, CombatStats::class)!!.atk()
    world.get(owner, OwnerCombatStats::class)!!.clearDirty()

    repeat(4) { world.get(owner, Level::class)!!.inc() }
    recalc(owner)

    assertEquals(before + 1, world.get(owner, CombatStats::class)!!.atk(), "four levels is one more BaseLv/4")
    assertSame(world.get(owner, CombatStats::class)!!.values, world.get(owner, OwnerCombatStats::class)!!.values)
    assertTrue(world.get(owner, OwnerCombatStats::class)!!.isDirty())
  }

  @Test
  fun `a level up fills health and mana to the new maxima`() {
    val owner = spawnOwnerWithVitals()
    recalc(owner)
    val health = world.get(owner, Health::class)!!
    val mana = world.get(owner, Mana::class)!!
    val (maxHpBefore, maxManaBefore) = health.max to mana.max

    repeat(4) { world.get(owner, Level::class)!!.inc() }
    world.add(owner, RefillVitals)
    recalc(owner)

    assertTrue(health.max > maxHpBefore && mana.max > maxManaBefore, "the maxima were rebuilt from the new level first")
    assertEquals(health.max, health.current)
    assertEquals(mana.max, mana.current)
    assertFalse(world.has(owner, RefillVitals::class))
  }

  @Test
  fun `a recalc that is no level up leaves health and mana where they were`() {
    val owner = spawnOwnerWithVitals()

    world.get(owner, Level::class)!!.inc()
    recalc(owner)

    assertEquals(10, world.get(owner, Health::class)!!.current)
    assertEquals(5, world.get(owner, Mana::class)!!.current)
  }

  @Test
  fun `a level up does not stand the dead back up`() {
    val owner = spawnOwnerWithVitals()
    world.get(owner, Health::class)!!.current = 0
    world.add(owner, Dead())

    world.get(owner, Level::class)!!.inc()
    world.add(owner, RefillVitals)
    recalc(owner)

    assertEquals(0, world.get(owner, Health::class)!!.current)
    assertFalse(world.has(owner, RefillVitals::class))
  }

  @Test
  fun `a recalc that changes nothing sends the owner nothing`() {
    val owner = spawnOwner()
    world.get(owner, OwnerCombatStats::class)!!.clearDirty()

    recalc(owner)

    assertFalse(world.get(owner, OwnerCombatStats::class)!!.isDirty())
  }

  @Test
  fun `an invested attribute reaches the block and the owner's outbound copy`() {
    val owner = spawnOwner()
    val before = world.get(owner, CombatStats::class)!!.atk()
    val outbound = world.get(owner, StatusValues::class)!!
    outbound.clearDirty()

    world.get(owner, BaseStatusValues::class)!!.strength = 16
    recalc(owner)

    // Ten more strength, and the squared term goes from 0 to 1.
    assertEquals(before + 10 + 1, world.get(owner, CombatStats::class)!!.atk())
    assertEquals(16, outbound.strength)
    assertTrue(outbound.isDirty())
  }

  @Test
  fun `an entity with nothing to rebuild from only has its marker cleared`() {
    val id = world.createEntity { add(it, Level(5)) }

    recalc(id)

    assertNull(world.get(id, CombatStats::class))
    assertFalse(world.has(id, IsStatusValueDirty::class))
  }
}
