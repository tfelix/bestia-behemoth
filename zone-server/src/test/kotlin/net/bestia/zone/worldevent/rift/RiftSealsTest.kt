package net.bestia.zone.worldevent.rift

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.WorldObjectResidencyService
import net.bestia.zone.prop.WorldObjectSite
import net.bestia.zone.world.time.BestiaClock
import net.bestia.zone.worldevent.persistence.StoredRift
import net.bestia.zone.worldevent.persistence.StoredRift.Phase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RiftSealsTest {

  private val world = testWorld()
  private val statusEffects = mockk<StatusEffectService>(relaxed = true)
  private val site = mockk<WorldObjectSite>()
  private val sealStones = mockk<SealStones> { every { raise(any(), any()) } returns site }
  private val residency = mockk<WorldObjectResidencyService>(relaxed = true)
  private val clock = mockk<BestiaClock> { every { speedFactor } returns 3.0 }
  private val sut = RiftSeals(RiftConfig(), statusEffects, sealStones, residency, clock)

  private val gate = world.createEntity { id -> add(id, Position.fromVec3(Vec3L(100, 200, 5))) }
  private val rift = StoredRift(phase = Phase.OPEN, stage = 3, reservoir = 5_000.0, anchorSlain = true).also { it.id = 4 }

  @Test
  fun `the anchor's fall opens a seal window at the rift's Potency`() {
    assertFalse(sut.tend(world, rift, gate, now = 1_000))

    verify { statusEffects.applyEffect(world, gate, StatusEffectId.RIFT_SEAL_WINDOW, 1, potency = 100 + 30 * 3) }
    assertEquals(1_000 + 30 * 60 * 3L, rift.sealWindowEndsAtSecond)
  }

  @Test
  fun `lifting the hold in time seals the rift into a stone at the gate`() {
    sut.tend(world, rift, gate, now = 1_000)

    assertTrue(sut.tend(world, rift, gate, now = 1_060), "no hold on the gate any more: it was dispelled")

    assertEquals(Phase.SEALED, rift.phase)
    assertEquals(0.0, rift.reservoir)
    verify { sealStones.raise(Vec3L(100, 200, 5), 5_000.0) }
    verify { residency.placeNow(world, site) }
  }

  @Test
  fun `a window that passes calls the anchor again at the next stage`() {
    sut.tend(world, rift, gate, now = 1_000)
    holdOnGate()

    assertFalse(sut.tend(world, rift, gate, now = 1_060), "still held")
    assertFalse(sut.tend(world, rift, gate, now = 1_000 + 30 * 60 * 3L))

    assertEquals(Phase.OPEN, rift.phase)
    assertFalse(rift.anchorSlain)
    assertEquals(4, rift.nextAnchorStage)
  }

  private fun holdOnGate() = world.add(gate, StatusEffects().apply {
    applyEffect(StatusEffectId.RIFT_SEAL_WINDOW.id, StackBehavior.IGNORE_IF_PRESENT, 1, null, durationSeconds = 1800.0, isSyncedToClient = false)
  })
}
