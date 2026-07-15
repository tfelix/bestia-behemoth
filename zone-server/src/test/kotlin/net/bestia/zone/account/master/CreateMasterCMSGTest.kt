package net.bestia.zone.account.master

import net.bestia.bnet.proto.CreateMasterProto
import net.bestia.bnet.proto.MasterProto
import org.junit.jupiter.api.Test
import java.awt.Color
import kotlin.test.assertEquals

/**
 * Unit test for the Envelope -> internal CMSG mapping used on the network path.
 */
class CreateMasterCMSGTest {

  @Test
  fun `fromBnet maps all fields including colors and enums`() {
    val proto = CreateMasterProto.CreateMasterCMSG.newBuilder()
      .setName("hero")
      .setBody(MasterProto.BodyType.BODY_M_1)
      .setFace(MasterProto.Face.FACE_1)
      .setHair(MasterProto.Hairstyle.HAIR_1)
      .setHairColor(MasterProto.Color.newBuilder().setR(10).setG(20).setB(30))
      .setSkinColor(MasterProto.Color.newBuilder().setR(200).setG(150).setB(100))
      .build()

    val result = CreateMasterCMSG.fromBnet(42L, proto)

    assertEquals(42L, result.playerId)
    assertEquals("hero", result.name)
    assertEquals(BodyType.BODY_M_1, result.body)
    assertEquals(Face.FACE_1, result.face)
    assertEquals(Hairstyle.HAIR_1, result.hair)
    assertEquals(Color(10, 20, 30), result.hairColor)
    assertEquals(Color(200, 150, 100), result.skinColor)
  }
}
