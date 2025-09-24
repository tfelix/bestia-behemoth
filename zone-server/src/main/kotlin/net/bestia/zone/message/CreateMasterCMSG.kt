package net.bestia.zone.message

import net.bestia.zone.account.master.BodyType
import net.bestia.zone.account.master.Face
import net.bestia.zone.account.master.Hairstyle
import java.awt.Color

data class CreateMasterCMSG(
  override val playerId: Long,
  val name: String,
  val hairColor: Color,
  val skinColor: Color,
  val hair: Hairstyle,
  val face: Face,
  val body: BodyType
) : CMSG {
  companion object
}
