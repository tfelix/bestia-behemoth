package net.bestia.zone.prop.chest

import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.ecs.ChestStorage
import net.bestia.zone.item.ecs.ExchangeReach
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Only the master who built a chest, or one of its bestias, standing next to it, gets to look inside. */
class ChestInteractionTest {

  private val world = testWorld()
  private val messages = mockk<OutMessageProcessor>(relaxed = true)
  private val sut = ChestInteraction(messages)

  private val contents = Inventory(mutableListOf(Inventory.Item(itemId = 40L, amount = 3)))

  private val chest = world.createEntity { id ->
    add(id, PropPose(Vec3L(5, 5, 0), yaw = 0f))
    add(id, ChestStorage(structureId = 33L, ownerMasterId = MASTER_ID, ownerAccountId = ACCOUNT_ID))
    add(id, contents)
  }

  private fun master(masterId: Long, accountId: Long, x: Long = 6): EntityId = world.createEntity { id ->
    add(id, Position(x, 5, 0))
    add(id, Master(masterId))
    add(id, Account(accountId))
  }

  @Test
  fun `the owner opens it and is sent what is inside`() {
    val owner = master(MASTER_ID, ACCOUNT_ID)
    contents.clearDirty()

    assertNull(sut.interact(world, owner, chest))

    verify { messages.sendToPlayer(ACCOUNT_ID, ChestOpenedSMSG(chest, ChestStorage.SLOTS)) }
    assertTrue(contents.isDirty(), "the contents go out with the next sync")
  }

  @Test
  fun `the owner's bestia opens it too`() {
    val bestia = world.createEntity { id ->
      add(id, Position(6, 5, 0))
      add(id, OwnedBestia(MASTER_ID, playerBestiaId = 21L))
      add(id, Account(ACCOUNT_ID))
    }

    assertNull(sut.interact(world, bestia, chest))
  }

  @Test
  fun `a stranger is refused`() {
    val stranger = master(MASTER_ID + 1, ACCOUNT_ID + 1)

    assertEquals(OpError.CHEST_NOT_YOURS, sut.interact(world, stranger, chest))
    verify(exactly = 0) { messages.sendToPlayer(any(), any<ChestOpenedSMSG>()) }
  }

  @Test
  fun `the owner too far away is told to walk up`() {
    val owner = master(MASTER_ID, ACCOUNT_ID, x = 5 + ExchangeReach.HORIZONTAL + 1)

    assertEquals(OpError.CHEST_OUT_OF_REACH, sut.interact(world, owner, chest))
  }

  private companion object {
    const val ACCOUNT_ID = 1L
    const val MASTER_ID = 9L
  }
}
