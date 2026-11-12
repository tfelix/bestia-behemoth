package net.bestia.zone.battle.status.scripts

import io.mockk.mockk
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.status.Taunted
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.testWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProvokedTest {

  private val sut = Provoked()
  private val world = testWorld()

  @Test
  fun `the provoked creature is taunted by the provoker until the effect ends`() {
    val knight = world.createEntity { }
    val wolf = world.createEntity { }
    val effect = ActiveStatusEffect(StatusEffectId.PROVOKED.id, 1, 4f, sourceEntityId = knight)
    val context = StatusEffectHookContext(world, wolf, effect, mockk(relaxed = true))

    sut.onApply(context)
    assertEquals(Taunted(knight), world.get(wolf, Taunted::class))

    sut.onRemove(context, RemovalReason.EXPIRED)
    assertNull(world.get(wolf, Taunted::class))
  }

  @Test
  fun `it lasts 4 to 8 seconds by Provoke's level`() {
    assertEquals(listOf(4.0, 5.0, 6.0, 7.0, 8.0), (1..5).map { sut.durationSeconds(it) })
  }
}
