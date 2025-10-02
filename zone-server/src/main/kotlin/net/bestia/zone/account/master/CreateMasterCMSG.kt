package net.bestia.zone.account.master

import net.bestia.zone.message.CMSG
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
