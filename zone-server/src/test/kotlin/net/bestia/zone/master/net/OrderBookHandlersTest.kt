package net.bestia.zone.master.net

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.BestiaJob
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.master.bestia.NamedPlaceService
import net.bestia.zone.master.bestia.OrderBook
import net.bestia.zone.master.bestia.OrderBookSMSG
import net.bestia.zone.master.bestia.OrderPresetService
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.session.ConnectionInfoService.PlayerEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

/** The refusals of the orders window's writes, worded for the player only where they can act on them. */
class OrderBookHandlersTest {

  private val world = testWorld()
  private val view = mockk<WorldView>().also { view ->
    every { view.read<Any?>(any()) } answers { firstArg<World.() -> Any?>().invoke(world) }
  }
  private val sessions = mockk<ConnectionInfoService>()
  private val book = mockk<OrderBook>()
  private val out = mockk<OutMessageProcessor>(relaxed = true)
  private val places = mockk<NamedPlaceService>()
  private val presets = mockk<OrderPresetService>()

  private val bestiaEntity = world.createEntity { id -> add(id, Position(5, 6, 0)) }

  init {
    every { sessions.activeMasterIdOf(ACCOUNT) } returns MASTER
    every { sessions.ownedBestiaOf(ACCOUNT, BESTIA) } returns PlayerEntity(BESTIA, bestiaEntity)
    every { sessions.ownedBestiaOf(ACCOUNT, neq(BESTIA)) } returns null
    every { book.of(MASTER) } returns OrderBookSMSG(emptyList(), emptyList())
  }

  private fun sent(): SMSG {
    val msg = slot<SMSG>()
    verify { out.sendToPlayer(ACCOUNT, capture(msg)) }
    return msg.captured
  }

  private fun refusalSent() = (sent() as OperationErrorSMSG).code

  private val workAreas = SaveWorkAreaHandler(sessions, places, view, book, out)

  @Test
  fun `a work area is named around where the bestia stands`() {
    every { places.saveArea(MASTER, null, "Field", any(), 16) } returns NamedPlaceService.Saved(null, null)

    workAreas.handle(SaveWorkAreaCMSG(ACCOUNT, BESTIA, "Field", 16))

    verify { places.saveArea(MASTER, null, "Field", Vec3L(5, 6, 0), 16) }
    assertInstanceOf(OrderBookSMSG::class.java, sent())
  }

  @Test
  fun `a taken name is told to the player`() {
    every { places.saveArea(any(), any(), any(), any(), any()) } returns
      NamedPlaceService.Saved(null, NamedPlaceService.Refusal.NAME_TAKEN)

    workAreas.handle(SaveWorkAreaCMSG(ACCOUNT, BESTIA, "Field", 16))

    assertEquals(OpError.ORDERS_NAME_TAKEN, refusalSent())
  }

  @Test
  fun `a bestia of someone else names no area`() {
    workAreas.handle(SaveWorkAreaCMSG(ACCOUNT, BESTIA + 1, "Field", 16))

    verify(exactly = 0) { places.saveArea(any(), any(), any(), any(), any()) }
    assertEquals(OpError.AI_CONFIG_BESTIA_NOT_OWNED, refusalSent())
  }

  private val presetWrites = SaveOrderPresetHandler(sessions, presets, book, out)

  @Test
  fun `a master with every preset slot used is told so`() {
    every { presets.save(MASTER, null, "Miners", any()) } returns
      OrderPresetService.Saved(null, OrderPresetService.Refusal.TOO_MANY)

    presetWrites.handle(SaveOrderPresetCMSG(ACCOUNT, null, "Miners", AiConfig(BestiaJob.MINE)))

    assertEquals(OpError.ORDERS_TOO_MANY_PRESETS, refusalSent())
  }

  @Test
  fun `orders the server cannot read save no preset`() {
    presetWrites.handle(SaveOrderPresetCMSG(ACCOUNT, null, "Miners", orders = null))

    verify(exactly = 0) { presets.save(any(), any(), any(), any()) }
    assertEquals(OpError.AI_CONFIG_INVALID_ORDERS, refusalSent())
  }

  private companion object {
    const val ACCOUNT = 1L
    const val MASTER = 2L
    const val BESTIA = 5L
  }
}
