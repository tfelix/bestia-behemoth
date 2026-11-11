package net.bestia.zone.battle.status.scripts

import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectTickContext
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import org.junit.jupiter.api.Test

class RuwachPulseTest {

  private val world = testWorld()
  private val statusEffects = mockk<StatusEffectService>(relaxed = true)

  @Test
  fun `a pulse ends hiding and cloaking within 10 m, whatever their potency`() {
    val caster = standing(Vec3L(0, 0, 0))
    val hidden = standing(Vec3L(5, 0, 0), StatusEffectId.HIDDEN, potency = 10_000)
    val cloakedFarAway = standing(Vec3L(30, 0, 0), StatusEffectId.CLOAKED)
    val effect = ActiveStatusEffect(definitionId = StatusEffectId.RUWACH.id, level = 1, remainingSeconds = 10f)

    RuwachPulse().onTick(StatusEffectTickContext(world, caster, effect, statusEffects))

    verify { statusEffects.removeEffect(world, hidden, StatusEffectId.HIDDEN.id, RemovalReason.DISPELLED) }
    verify(exactly = 0) { statusEffects.removeEffect(world, cloakedFarAway, any(), any()) }
  }

  private fun standing(at: Vec3L, concealment: StatusEffectId? = null, potency: Int = 100) = world.createEntity { id ->
    add(id, Position.fromVec3(at))
    val effects = concealment?.let {
      mutableListOf(ActiveStatusEffect(definitionId = it.id, level = 1, remainingSeconds = 60f, potency = potency))
    } ?: mutableListOf()
    add(id, StatusEffects(effects))
  }
}
