package net.bestia.zone.battle.status.scripts

import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.status.EntityAction
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.ecs.core.testWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SprintingTest {

  private val sut = Sprinting()
  private val world = testWorld()

  @Test
  fun `each rank runs faster`() {
    val speeds = (1..3).map { rank ->
      val context = StatusValueRecalcContext(StatusValues(1, 1, 1, 1, 1, 1), baseSpeed = 10f)
      sut.apply(world, 1L, context, ActiveStatusEffect(StatusEffectId.SPRINTING.id, rank, 5f))
      context.speed
    }

    assertEquals(listOf(13f, 14f, 15f), speeds)
  }

  @Test
  fun `an attack or a cast ends the sprint, an item does not`() {
    val service = mockk<StatusEffectService>(relaxed = true)
    val effect = ActiveStatusEffect(StatusEffectId.SPRINTING.id, 1, 5f)
    val context = StatusEffectHookContext(world, 7L, effect, service)

    sut.onActed(context, EntityAction.USED_ITEM)
    sut.onActed(context, EntityAction.ATTACKED)

    verify(exactly = 1) { service.removeEffect(world, 7L, StatusEffectId.SPRINTING.id, RemovalReason.CANCELLED) }
  }
}
