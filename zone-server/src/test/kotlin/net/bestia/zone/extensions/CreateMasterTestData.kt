package net.bestia.zone.extensions

import net.bestia.zone.message.CreateMasterCMSG
import net.bestia.zone.account.master.BodyType
import net.bestia.zone.account.master.Face
import net.bestia.zone.account.master.Hairstyle
import net.bestia.zone.message.CMSG
import java.awt.Color

fun CreateMasterCMSG.Companion.test(
  playerId: Long,
  name: String = "master"
): CMSG {
  return CreateMasterCMSG(
    playerId = playerId,
    name = name,
    hairColor = Color.BLUE,
    skinColor = Color.BLUE,
    hair = Hairstyle.HAIR_1,
    face = Face.FACE_1,
    body = BodyType.BODY_M_1
  )
}