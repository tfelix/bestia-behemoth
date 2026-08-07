package net.bestia.zone.account.master

import net.bestia.bnet.proto.DeleteMasterProto
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Unit test for the Envelope -> internal CMSG mapping used on the network path.
 */
class DeleteMasterCMSGTest {

  @Test
  fun `fromBnet maps the master id and the typed confirmation name`() {
    val proto = DeleteMasterProto.DeleteMasterCMSG.newBuilder()
      .setMasterId(17L)
      .setConfirmationName("hero")
      .build()

    val result = DeleteMasterCMSG.fromBnet(42L, proto)

    assertEquals(42L, result.playerId)
    assertEquals(17L, result.masterId)
    assertEquals("hero", result.confirmationName)
  }

  @Test
  fun `fromBnet passes a wrong confirmation name through verbatim rather than sanitizing it`() {
    // The mismatch has to survive the mapping - MasterDeletionService is what refuses it, and it can only
    // do that if it sees exactly what the player typed.
    val proto = DeleteMasterProto.DeleteMasterCMSG.newBuilder()
      .setMasterId(17L)
      .setConfirmationName("not the hero")
      .build()

    assertEquals("not the hero", DeleteMasterCMSG.fromBnet(42L, proto).confirmationName)
  }
}
