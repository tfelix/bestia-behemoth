package net.bestia.zone.extensions

import net.bestia.zone.master.CreateMasterCMSG
import net.bestia.zone.account.BodyType
import net.bestia.zone.account.Face
import net.bestia.zone.account.Hairstyle
import net.bestia.zone.master.MasterFactory
import net.bestia.zone.master.status.StatusAttribute
import net.bestia.zone.message.CMSG
import java.awt.Color

fun CreateMasterCMSG.Companion.test(
  playerId: Long,
  name: String = "master",
  spawnPointId: Int,
  effortValues: Map<StatusAttribute, Int> = MasterFactory.evenlySpreadEffortValues()
): CMSG {
  return CreateMasterCMSG(
    playerId = playerId,
    name = name,
    hairColor = Color.BLUE,
    skinColor = Color.BLUE,
    hair = Hairstyle.HAIR_1,
    face = Face.FACE_1,
    body = BodyType.BODY_M_1,
    spawnPointId = spawnPointId,
    effortValues = effortValues
  )
}