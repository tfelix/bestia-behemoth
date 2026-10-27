package net.bestia.zone.item.net

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.container.ReservedItem
import net.bestia.zone.item.ecs.CarryCapacity
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.EquipmentSlots
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The goods leave the giver's live bag when the move is checked and reach the receiver once the database has
 * moved them. Every way that can go wrong ends with the goods in one bag, never two.
 */
class TransferItemHandlerTest {

  private val world = testWorld()
  private val inventoryService = mockk<InventoryService>()
  private val outMessages = mockk<OutMessageProcessor>(relaxed = true)

  /** Holds the submitted job instead of running it, so the test decides when the database answers. */
  private var pending: (() -> Unit)? = null
  private var queueFull = false
  private val executor = mockk<AsyncJobExecutor>().also {
    every { it.trySubmit(MASTER_ID, any()) } answers {
      if (queueFull) false else true.also { pending = secondArg() }
    }
  }

  private val arrows = Inventory.Item(itemId = ARROW, amount = 10, weight = 10)

  private val master = world.createEntity { id ->
    add(id, Position(5, 5, 0))
    add(id, Master(MASTER_ID))
    add(id, Inventory(mutableListOf(arrows)))
    add(id, Equipment(availableSlotMask = EquipmentSlots.ALL))
    add(id, CarryCapacity(current = 0, max = 10_000))
  }

  private val bestia = ownBestia(PLAYER_BESTIA_ID, x = 7)

  private val connections = ConnectionInfoService().also {
    it.activateSession(ACCOUNT_ID, masterId = MASTER_ID, masterEntityId = master)
  }

  private val sut = TransferItemHandler(inventoryService, connections, DeadActionGuard(), executor, outMessages, world)

  private val handOver = TransferItemCMSG(ACCOUNT_ID, master, bestia, arrows.playerItemId, amount = 4)

  private fun ownBestia(playerBestiaId: Long, x: Long, masterId: Long = MASTER_ID, capacity: Int = 10_000): EntityId =
    world.createEntity { id ->
      add(id, Position(x, 5, 0))
      add(id, OwnedBestia(masterId, playerBestiaId))
      add(id, Inventory(mutableListOf()))
      add(id, CarryCapacity(current = 0, max = capacity))
    }

  private fun arrowsIn(entity: EntityId): Int =
    world.get(entity, Inventory::class)!!.getItems().filter { it.itemId == ARROW }.sumOf { it.amount }

  private fun arrived(amount: Int) = ReservedItem(
    offerSlotId = 0L, itemId = ARROW, amount = amount, weight = 10, uniqueId = 0L, stackable = true,
    durability = 0, maxDurability = 0, slots = 0, upgradeLevel = 0, itemLevel = 1, rolledStats = emptyList(),
  )

  @Test
  fun `the goods leave the giver on the tick and reach the bestia once the database moved them`() {
    every { inventoryService.transferBetweenOwners(MASTER_ID, null, PLAYER_BESTIA_ID, ARROW, 0L, 4, null) } returns
      arrived(4)

    sut.handle(world, handOver)

    assertEquals(6, arrowsIn(master), "gone from the giver before the database answers")
    assertEquals(0, arrowsIn(bestia))

    pending!!.invoke()

    assertEquals(6, arrowsIn(master))
    assertEquals(4, arrowsIn(bestia))
  }

  @Test
  fun `a second move of the same goods in the same tick is refused`() {
    sut.handle(world, handOver.copy(amount = 10))
    val first = pending
    pending = null

    sut.handle(world, handOver.copy(amount = 10))

    assertNotNull(first)
    assertNull(pending, "nothing left to move twice")
  }

  @Test
  fun `a rolled back move gives the goods back`() {
    every { inventoryService.transferBetweenOwners(any(), any(), any(), any(), any(), any(), any()) } throws
      IllegalStateException("deadlock")

    sut.handle(world, handOver)
    pending!!.invoke()

    assertEquals(10, arrowsIn(master))
    assertEquals(0, arrowsIn(bestia))
  }

  @Test
  fun `goods the database does not hold are not given back`() {
    every { inventoryService.transferBetweenOwners(any(), any(), any(), any(), any(), any(), any()) } returns null

    sut.handle(world, handOver)
    pending!!.invoke()

    assertEquals(6, arrowsIn(master), "the live bag showed more than the database had")
    assertEquals(0, arrowsIn(bestia))
  }

  @Test
  fun `a move the job queue cannot take stays with the giver`() {
    queueFull = true

    sut.handle(world, handOver)

    assertEquals(10, arrowsIn(master))
  }

  @Test
  fun `a bestia too far away is told so and nothing moves`() {
    val farAway = ownBestia(PLAYER_BESTIA_ID + 1, x = 5 + ExchangeReach.HORIZONTAL + 1)

    sut.handle(world, handOver.copy(toEntityId = farAway))

    assertNull(pending)
    assertEquals(10, arrowsIn(master))
    verify { outMessages.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.EXCHANGE_OUT_OF_REACH)) }
  }

  @Test
  fun `a bestia on the ledge above is out of reach`() {
    world.get(bestia, Position::class)!!.apply { z = ExchangeReach.VERTICAL + 1 }

    sut.handle(world, handOver)

    assertNull(pending)
  }

  @Test
  fun `a bestia that cannot carry it is told so and nothing moves`() {
    val weak = ownBestia(PLAYER_BESTIA_ID + 1, x = 6, capacity = 39)

    sut.handle(world, handOver.copy(toEntityId = weak))

    assertNull(pending)
    assertEquals(10, arrowsIn(master))
    verify { outMessages.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.EXCHANGE_TOO_HEAVY)) }
  }

  @Test
  fun `another master's bestia is neither given to nor taken from`() {
    val strangers = ownBestia(PLAYER_BESTIA_ID + 1, x = 6, masterId = MASTER_ID + 1)
    world.get(strangers, Inventory::class)!!.addItem(Inventory.Item(ARROW, amount = 5))
    val theirArrows = world.get(strangers, Inventory::class)!!.getItems().single().playerItemId

    sut.handle(world, handOver.copy(toEntityId = strangers))
    sut.handle(world, TransferItemCMSG(ACCOUNT_ID, strangers, master, theirArrows, amount = 5))

    assertNull(pending)
    assertEquals(5, arrowsIn(strangers))
  }

  @Test
  fun `worn gear stays on`() {
    world.get(master, Inventory::class)!!.addItem(Inventory.Item(SWORD, amount = 1, uniqueId = 77L, stackable = false))
    world.get(master, Equipment::class)!!
      .equip(EquipmentSlot.RIGHT_HAND, Equipment.EquippedItem(itemId = SWORD, uniqueId = 77L))

    sut.handle(world, TransferItemCMSG(ACCOUNT_ID, master, bestia, playerItemId = 77L, amount = 1))

    assertNull(pending)
  }

  @Test
  fun `a dead bestia receives nothing`() {
    world.add(bestia, Dead())

    sut.handle(world, handOver)

    assertNull(pending)
    assertEquals(10, arrowsIn(master))
  }

  @Test
  fun `more than is held, nothing, or a move to itself is refused`() {
    sut.handle(world, handOver.copy(amount = 11))
    sut.handle(world, handOver.copy(amount = 0))
    sut.handle(world, handOver.copy(toEntityId = master))

    assertNull(pending)
    assertEquals(10, arrowsIn(master))
  }

  private companion object {
    const val ACCOUNT_ID = 1L
    const val MASTER_ID = 9L
    const val PLAYER_BESTIA_ID = 21L
    const val ARROW = 40L
    const val SWORD = 11L
  }
}
