package net.bestia.zone.entity

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.CMSG

data class MoveActiveEntityCMSG(
  override val playerId: Long,
  val path: List<Vec3L>
) : CMSG