package net.bestia.zone.boot

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.status.scripts.WardAura
import net.bestia.zone.battle.status.scripts.Warded
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.script.ecs.ScriptEntitySpawner
import net.bestia.zone.script.persistence.ScriptEntityPersister
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class WardStoneBootRunnerTest {

  private val world = testWorld()
  private val catalogue = StatusEffectDefinitionRegistry().also { StatusEffectImporterBootRunner(it).run() }
  private val statusEffects = StatusEffectService(catalogue, StatusEffectScriptRegistry(listOf(WardAura(), Warded())))
  private val runner = WardStoneBootRunner(world, statusEffects)

  private val spawner = ScriptEntitySpawner()

  @Test
  fun `the script entity at a spawn point becomes a visible stone carrying the aura`() {
    val stone = spawner.spawnScript(world, Vec3L(10, 10, 64), ScriptEntityPersister.SPAWN_POINT_SCRIPT_ID)

    runner.run()

    assertEquals(
      EntityVisual(VisualKind.STRUCTURE, StaticEntityKind.WARD_STONE.ordinal.toLong()),
      world.get(stone, EntityVisual::class)
    )
    assertEquals(listOf(StatusEffectId.WARD_AURA.id), auraIdsOf(stone))
  }

  @Test
  fun `raising the stones twice leaves one aura on each`() {
    val stone = spawner.spawnScript(world, Vec3L(10, 10, 64), ScriptEntityPersister.SPAWN_POINT_SCRIPT_ID)

    runner.run()
    runner.run()

    assertEquals(listOf(StatusEffectId.WARD_AURA.id), auraIdsOf(stone))
  }

  @Test
  fun `another script entity is left alone`() {
    val other = spawner.spawnScript(world, Vec3L(10, 10, 64), "something_else")

    runner.run()

    assertNull(world.get(other, EntityVisual::class))
    assertFalse(world.has(other, StatusEffects::class))
  }

  private fun auraIdsOf(stone: Long): List<Long> {
    return world.getOrThrow(stone, StatusEffects::class).activeEffects.map { it.definitionId }
  }
}
