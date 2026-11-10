package net.bestia.zone.fishing.net

import net.bestia.bnet.proto.CastLineCmsgProto
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.CMSG

/** The active master casts a fishing line onto the water at [target]. */
data class CastLineCMSG(
  override val playerId: Long,
  val target: Vec3L,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, castLine: CastLineCmsgProto.CastLineCMSG): CastLineCMSG {
      return CastLineCMSG(accountId, Vec3L(castLine.target.x, castLine.target.y, castLine.target.z))
    }
  }
}
