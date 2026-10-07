package net.bestia.zone.battle.persistence

import io.mockk.mockk
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectDefinition
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.util.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class StatusEffectPersistenceServiceTest {

  private val world = testWorld()

  private val definitions = StatusEffectDefinitionRegistry().apply {
    load(
      listOf(
        StatusEffectDefinition(id = SAVED, identifier = "SAVED", isSyncedToClient = true, script = "Saved"),
        StatusEffectDefinition(id = RE_APPLIED, identifier = "RE_APPLIED", isSyncedToClient = false, script = "ReApplied"),
      )
    )
  }

  private val scripts = StatusEffectScriptRegistry(listOf(Saved(), ReApplied()))

  private val service = StatusEffectPersistenceService(mockk(relaxed = true), definitions, scripts)

  @Test
  fun `an effect that is not persisted stays out of the snapshot`() {
    val id = entityWith(effect(SAVED), effect(RE_APPLIED))

    val snapshot = assertNotNull(service.snapshot(world, id))

    assertEquals(listOf(SAVED), snapshot.effects.map { it.definitionId })
  }

  @Test
  fun `attaching stored effects keeps the ones the spawner already applied`() {
    val id = entityWith(effect(RE_APPLIED))

    service.attach(world, id, listOf(effect(SAVED)))

    val effects = world.getOrThrow(id, StatusEffects::class).activeEffects
    assertEquals(setOf(RE_APPLIED, SAVED), effects.map { it.definitionId }.toSet())
  }

  private fun entityWith(vararg effects: ActiveStatusEffect): EntityId {
    return world.createEntity { id -> world.add(id, StatusEffects(effects.toMutableList())) }
  }

  private fun effect(definitionId: Long): ActiveStatusEffect {
    return ActiveStatusEffect(definitionId = definitionId, level = 1, remainingSeconds = 10f)
  }

  private class Saved : StatusEffectScript {
    override fun durationSeconds(level: Int): Double {
      return 10.0
    }
  }

  private class ReApplied : StatusEffectScript {
    override val isPersisted: Boolean = false

    override fun durationSeconds(level: Int): Double {
      return 10.0
    }
  }

  private companion object {
    const val SAVED = 1L
    const val RE_APPLIED = 2L
  }
}
