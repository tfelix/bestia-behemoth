package net.bestia.zone.account.master

import net.bestia.bnet.proto.CreateMasterProto
import net.bestia.bnet.proto.MasterProto
import net.bestia.zone.account.master.status.StatusAttribute
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
      .setSpawnPointId(7)
      .setEffortValues(
        MasterProto.EffortValues.newBuilder()
          .setStrength(1)
          .setAgility(2)
          .setVitality(3)
          .setIntelligence(4)
          .setDexterity(5)
          .setWillpower(6)
      )
      .build()

    val result = CreateMasterCMSG.fromBnet(42L, proto)

    assertEquals(42L, result.playerId)
    assertEquals("hero", result.name)
    assertEquals(BodyType.BODY_M_1, result.body)
    assertEquals(Face.FACE_1, result.face)
    assertEquals(Hairstyle.HAIR_1, result.hair)
    assertEquals(Color(10, 20, 30), result.hairColor)
    assertEquals(Color(200, 150, 100), result.skinColor)
    // Passed through verbatim, including the unset 0 - the factory is what refuses an id no spawn point has.
    assertEquals(7, result.spawnPointId)
    // Mapped per attribute rather than positionally, so a proto field reorder can't silently swap two
    // attributes. The distribution itself is nonsense here on purpose - validation lives in the factory.
    assertEquals(
      mapOf(
        StatusAttribute.STRENGTH to 1,
        StatusAttribute.AGILITY to 2,
        StatusAttribute.VITALITY to 3,
        StatusAttribute.INTELLIGENCE to 4,
        StatusAttribute.DEXTERITY to 5,
        StatusAttribute.WILLPOWER to 6
      ),
      result.effortValues
    )
  }
}
