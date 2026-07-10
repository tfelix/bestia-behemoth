package net.bestia.zone.entity

import net.bestia.bnet.proto.MoveActiveEntityProto
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.CMSG

data class MoveActiveEntityCMSG(
  override val playerId: Long,
  val path: List<Vec3L>
) : CMSG {
  companion object {
    fun fromBnet(
      accountId: Long,
      moveActiveEntity: MoveActiveEntityProto.MoveActiveEntity
    ): MoveActiveEntityCMSG {
      return MoveActiveEntityCMSG(
        accountId,
        moveActiveEntity.pathList.map { Vec3L(it.x, it.y, it.z) }
      )
    }
  }
}