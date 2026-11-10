package net.bestia.zone.treasure.net

import net.bestia.bnet.proto.DigCMSGProto
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.CMSG

/** A request to dig at a ground tile, with the tool in the right hand. */
data class DigCMSG(
  override val playerId: Long,
  val position: Vec3L
) : CMSG {

  companion object {
    fun fromBnet(playerId: Long, bnet: DigCMSGProto.DigCMSG): DigCMSG {
      return DigCMSG(playerId, Vec3L(bnet.position.x, bnet.position.y, bnet.position.z))
    }
  }
}
