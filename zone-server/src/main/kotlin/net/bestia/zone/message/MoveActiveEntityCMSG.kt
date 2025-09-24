package net.bestia.zone.message

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.ecs.message.InECSMessage
import net.bestia.bnet.proto.MoveActiveEntityProto

data class MoveActiveEntityCMSG(
  override val playerId: Long,
  val path: List<Vec3L>
) : CMSG, InECSMessage {

  companion object {
    fun fromBnet(
      accountId: Long,
      moveActiveEntity: MoveActiveEntityProto.MoveActiveEntity
    ): MoveActiveEntityCMSG {
      return MoveActiveEntityCMSG(accountId, moveActiveEntity.pathList.map { p -> Vec3L(p.x, p.y, p.z) })
    }
  }
}