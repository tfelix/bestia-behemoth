package net.bestia.zone.honor

import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.honor.ecs.Honor
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HonorStandingSystemTest {

  private val fixture = HonorFixture { f -> listOf(HonorStandingSystem(f.service, f.statusEffects)) }
  private val world = fixture.world

  @Test
  fun `a master below zero is marked, and the mark goes once the honor heals`() {
    val outlaw = fixture.master(Vec3L(0, 0, 0), HonorLedger(debt = 21, lastLossAt = fixture.now))

    world.tick(10f)
    assertTrue(isDishonored(outlaw))

    fixture.advance(Duration.ofDays(3))
    repeat(4) { world.tick(10f) }

    assertEquals(1, world.get(outlaw, Honor::class)!!.shownValue)
    assertFalse(isDishonored(outlaw))
  }

  private fun isDishonored(entity: Long): Boolean {
    return world.get(entity, StatusEffects::class)!!.hasEffect(StatusEffectId.DISHONORED.id)
  }
}
