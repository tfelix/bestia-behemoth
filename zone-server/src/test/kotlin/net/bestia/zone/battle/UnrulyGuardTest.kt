package net.bestia.zone.battle

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ai.ecs.PlayerControlled
import net.bestia.zone.ai.ecs.PlayerHold
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.MasterEntityResolver
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import java.util.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnrulyGuardTest {

  private val world = testWorld()
  private val statusEffects = mockk<StatusEffectService>(relaxed = true)
  private val out = mockk<OutMessageProcessor>(relaxed = true)
  private val master = world.createEntity { id -> add(id, Level(MASTER_LEVEL)) }
  private val masters = mockk<MasterEntityResolver> {
    every { masterEntityOf(any(), any()) } returns master
  }

  @Test
  fun `the chance rises evenly from 20 levels above the master to certainty at 40`() {
    assertEquals(0.0, UnrulyGuard.chanceAt(-5))
    assertEquals(0.0, UnrulyGuard.chanceAt(20))
    assertEquals(0.05, UnrulyGuard.chanceAt(21), 1e-9)
    assertEquals(0.5, UnrulyGuard.chanceAt(30), 1e-9)
    assertEquals(1.0, UnrulyGuard.chanceAt(40))
    assertEquals(1.0, UnrulyGuard.chanceAt(60))
  }

  @Test
  fun `a bestia within 20 levels always obeys`() {
    val bestia = bestia(level = MASTER_LEVEL + 20)

    assertFalse(guard(rolling = 0.0).refuses(world, ACCOUNT, bestia))
    verify(exactly = 0) { statusEffects.applyEffect(any(), any(), any<Long>(), any()) }
  }

  @Test
  fun `a command that sets it off makes it unruly and tells its master once`() {
    val bestia = bestia(level = MASTER_LEVEL + 30)

    assertFalse(guard(rolling = 0.6).refuses(world, ACCOUNT, bestia), "60 is above the 50 % chance")
    assertTrue(guard(rolling = 0.4).refuses(world, ACCOUNT, bestia))

    verify(exactly = 1) { statusEffects.applyEffect(world, bestia, StatusEffectId.UNRULY.id, 1) }
    verify(exactly = 1) { out.sendToPlayer(ACCOUNT, any<OperationErrorSMSG>()) }
  }

  @Test
  fun `an unruly bestia refuses every command without telling again, and its own AI drives it`() {
    val bestia = bestia(level = MASTER_LEVEL + 1)
    world.modify(bestia) { id -> add(id, PlayerControlled) }
    assertTrue(PlayerHold.isHeld(world, bestia), "a driven bestia is held")

    makeUnruly(bestia)

    assertTrue(guard(rolling = 0.99).refuses(world, ACCOUNT, bestia))
    verify(exactly = 0) { out.sendToPlayer(any(), any<net.bestia.zone.message.SMSG>()) }
    assertFalse(PlayerHold.isHeld(world, bestia), "an unruly one is not, so its AI takes over")
  }

  @Test
  fun `a master is never unruly`() {
    assertFalse(guard(rolling = 0.0).refuses(world, ACCOUNT, master))
  }

  private fun guard(rolling: Double): UnrulyGuard {
    val random = object : Random() {
      override fun nextDouble(): Double = rolling
    }

    return UnrulyGuard(statusEffects, masters, out, random)
  }

  private fun bestia(level: Int): EntityId = world.createEntity { id ->
    add(id, Level(level))
    add(id, OwnedBestia(masterId = 1L, playerBestiaId = 1L))
  }

  private fun makeUnruly(bestia: EntityId) {
    world.modify(bestia) { id ->
      val effects = StatusEffects()
      effects.applyEffect(
        definitionId = StatusEffectId.UNRULY.id,
        stackBehavior = StackBehavior.REFRESH_DURATION,
        level = 1,
        sourceEntityId = null,
        durationSeconds = 60.0,
        isSyncedToClient = true,
      )
      add(id, effects)
    }
  }

  private companion object {
    const val ACCOUNT = 7L
    const val MASTER_LEVEL = 10
  }
}
