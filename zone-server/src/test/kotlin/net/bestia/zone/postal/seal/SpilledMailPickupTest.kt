package net.bestia.zone.postal.seal

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.honor.HonorEvents
import net.bestia.zone.honor.StolenTier
import net.bestia.zone.identity.MasterEntityResolver
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.movement.ecs.Position
import org.junit.jupiter.api.Test

class SpilledMailPickupTest {

  private val honorEvents = mockk<HonorEvents>(relaxed = true)
  private val world = testWorld()
  private val thief = world.createEntity { id -> add(id, Account(ACCOUNT)) }
  private val masters = mockk<MasterEntityResolver> { every { selectedMasterEntityOf(ACCOUNT) } returns thief }
  private val rule = SpilledMailPickup(honorEvents, masters)

  @Test
  fun `taking spilled mail is stealing at the item's tier`() {
    val goods = world.createEntity { id ->
      add(id, SpilledMail(itemLevel = 34))
      add(id, Position.fromVec3(Vec3L(4, 5, 6)))
    }

    rule.onPickUp(world, thief, goods)

    verify { honorEvents.stole(world, thief, StolenTier.SUPERIOR, Vec3L(4, 5, 6)) }
  }

  @Test
  fun `an item that never was in the mail costs nothing`() {
    val apple = world.createEntity { id -> add(id, Position.fromVec3(Vec3L(4, 5, 6))) }

    rule.onPickUp(world, thief, apple)

    verify(exactly = 0) { honorEvents.stole(any(), any(), any(), any(), any()) }
  }

  private companion object {
    const val ACCOUNT = 3L
  }
}
