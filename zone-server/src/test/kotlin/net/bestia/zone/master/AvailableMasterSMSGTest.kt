package net.bestia.zone.master

import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Test
import java.awt.Color
import kotlin.test.assertEquals

class AvailableMasterSMSGTest {

  /** The selection screen draws each master where the wire says its slot is, so the number has to survive. */
  @Test
  fun `each master goes out with the slot it is pinned to`() {
    val smsg = AvailableMasterSMSG(
      master = listOf(info(id = 10, slot = 0), info(id = 11, slot = 2)),
      maxAvailableMasterSlots = 3,
      maxAvailableBestiaSlots = 4
    )

    val masters = smsg.toBnetEnvelope().master.masterList

    assertEquals(listOf(10L to 0, 11L to 2), masters.map { it.masterId to it.slot })
  }

  private fun info(id: Long, slot: Int) = AvailableMasterSMSG.MasterInfo(
    id = id,
    name = "master$id",
    hairColor = Color.BLACK,
    skinColor = Color.WHITE,
    hair = Hairstyle.HAIR_1,
    face = Face.FACE_1,
    body = BodyType.BODY_M_1,
    position = Vec3L.ZERO,
    level = 1,
    slot = slot,
    bestias = emptyList()
  )
}
