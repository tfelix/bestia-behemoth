package net.bestia.zone.blackboard

import net.bestia.zone.util.EntityId

/** Who stands at which board, read on the tick so the database work after it needs no world. */
data class BoardVisit(
  val accountId: Long,
  val masterId: Long,
  val masterEntityId: EntityId,
  val masterName: String,
  val boardEntityId: EntityId,
  val placeName: String,
)
