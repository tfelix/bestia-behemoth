package net.bestia.zone.master.bestia

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.bnet.proto.OperationSuccessProto.OpSuccess
import net.bestia.zone.battle.ecs.level.Level
import net.bestia.zone.crafting.EtchingOffer
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.master.bestia.ecs.EtchingOfferSystem
import net.bestia.zone.master.bestia.ecs.OfferedForEtching
import net.bestia.zone.message.OperationSuccessSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EtchingOffersTest {

  private val world = testWorld()

  private var tableInReach = true

  private val structures = mockk<PlayerStructureService> {
    every { stationNear(any(), any(), StaticEntityKind.CARVING_TABLE) } answers { if (tableInReach) 1L else null }
  }

  private val outMessageProcessor = mockk<OutMessageProcessor>(relaxed = true)

  private val owner = world.createEntity { id ->
    add(id, Master(masterId = OWNER_MASTER, name = "Bram"))
    add(id, Account(OWNER_ACCOUNT))
  }

  private val connections = mockk<ConnectionInfoService> {
    every { getSelectedMasterEntityId(OWNER_ACCOUNT) } returns owner
  }

  private val offers = EtchingOffers(structures, connections, outMessageProcessor)

  @Test
  fun `the owner offers a Bestia standing at the table`() {
    val boar = givenBestia()

    assertNull(offers.offer(world, owner, boar))

    assertTrue(offers.isOffered(world, boar))
    verify { outMessageProcessor.sendToPlayer(OWNER_ACCOUNT, OperationSuccessSMSG(OpSuccess.ETCHING_OFFERED)) }
  }

  @Test
  fun `nobody else may offer it, and not away from a table`() {
    val stranger = world.createEntity { add(it, Master(masterId = OWNER_MASTER + 1)) }
    val boar = givenBestia()

    assertEquals(OpError.REQUEST_REFUSED, offers.offer(world, stranger, boar))

    tableInReach = false
    assertEquals(OpError.CRAFT_BESTIA_NOT_AT_TABLE, offers.offer(world, owner, boar))
    assertFalse(world.has(boar, OfferedForEtching::class))
  }

  /** Otherwise an old offer would come back with the Bestia, long after its owner meant it. */
  @Test
  fun `the offer ends once the Bestia leaves the table`() {
    val boar = givenBestia()
    offers.offer(world, owner, boar)

    tableInReach = false
    EtchingOfferSystem(offers).update(world, 1f)
    tableInReach = true

    assertFalse(offers.isOffered(world, boar))
  }

  @Test
  fun `an Artificer sees other masters' offers at the table with their owner's name`() {
    val boar = givenBestia()
    offers.offer(world, owner, boar)

    assertEquals(listOf(EtchingOffer(boar, BOAR, 45, "Bram")), offers.offersAt(world, Vec3L(0, 0, 0), ARTIFICER_MASTER))
    assertEquals(emptyList<EtchingOffer>(), offers.offersAt(world, Vec3L(0, 0, 0), OWNER_MASTER))
    assertEquals(emptyList<EtchingOffer>(), offers.offersAt(world, Vec3L(50, 0, 0), ARTIFICER_MASTER))
  }

  private fun givenBestia(): EntityId = world.createEntity { id ->
    add(id, OwnedBestia(masterId = OWNER_MASTER, playerBestiaId = 1L))
    add(id, Account(OWNER_ACCOUNT))
    add(id, EntityVisual(VisualKind.BESTIA, BOAR))
    add(id, Level(45))
    add(id, Position(0, 0, 0))
  }

  private companion object {
    const val OWNER_MASTER = 42L
    const val ARTIFICER_MASTER = 43L
    const val OWNER_ACCOUNT = 7L
    const val BOAR = 6L
  }
}
