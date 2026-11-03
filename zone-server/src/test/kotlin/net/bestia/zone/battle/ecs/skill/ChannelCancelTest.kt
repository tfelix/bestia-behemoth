package net.bestia.zone.battle.ecs.skill

import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.testWorld
import kotlin.test.Test
import kotlin.test.assertFalse

class ChannelCancelTest {

  private val world = testWorld()

  @Test
  fun `breaking off a cast ends a channel and frees the caster`() {
    val caster = world.createEntity { id ->
      add(id, Channel(skillId = 1L, skillLevel = 1, targetEntityId = 2L, targetPosition = null, intervalSeconds = 0.5f, totalSeconds = 4f))
      add(id, StatusEffects(mutableListOf(ActiveStatusEffect(StatusEffectId.CHANNELING.id, level = 1, remainingSeconds = 4f))))
    }

    CastCancelService().cancelCast(world, caster)

    assertFalse(world.has(caster, Channel::class))
    assertFalse(world.getOrThrow(caster, StatusEffects::class).hasEffect(StatusEffectId.CHANNELING.id))
  }
}
