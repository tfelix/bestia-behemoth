package net.bestia.zone.ai.ecs

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.Stamina
import net.bestia.zone.battle.status.StatusEffectDefinition
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectPolarity
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.status.scripts.Resting
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.Animation
import net.bestia.zone.entity.ecs.Animation.AnimationKind
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A master rests while it sits or lies down, and stops resting when it stands up. */
class RestingSystemTest {

  private val resting = StatusEffectDefinition(
    id = StatusEffectId.RESTING.id, identifier = "RESTING", isSyncedToClient = true, script = "Resting",
    polarity = StatusEffectPolarity.BUFF
  )
  private val statusEffects = StatusEffectService(
    StatusEffectDefinitionRegistry().apply { load(listOf(resting)) }, StatusEffectScriptRegistry(listOf(Resting()))
  )
  private val world: EcsWorld = testWorld(systems = listOf(RestingSystem(statusEffects)))

  @Test
  fun `sitting down is resting`() {
    val master = master()

    world.get(master, Animation::class)!!.startEmote(AnimationKind.SIT)
    world.tick(RESTING_DUE)

    assertTrue(isResting(master))
  }

  @Test
  fun `standing up ends the rest`() {
    val master = master()
    world.get(master, Animation::class)!!.startEmote(AnimationKind.LIE)
    world.tick(RESTING_DUE)

    world.get(master, Animation::class)!!.endEmote()
    world.tick(RESTING_DUE)

    assertFalse(isResting(master))
  }

  @Test
  fun `a master on its feet is not resting`() {
    val master = master()

    world.tick(RESTING_DUE)

    assertFalse(isResting(master))
  }

  private fun master(): EntityId {
    val id = world.createEntity { }
    world.add(id, Master(1L))
    world.add(id, Stamina(current = 10, max = 100))
    world.add(id, Animation())
    return id
  }

  private fun isResting(id: EntityId): Boolean {
    return world.get(id, StatusEffects::class)?.hasEffect(StatusEffectId.RESTING.id) == true
  }

  private companion object {
    const val RESTING_DUE = 1.1f
  }
}
