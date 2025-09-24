package net.bestia.zone.message

import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.message.InECSMessage

/**
 * Kind of special message. I am not sure if this is a CMSG at all at no client should issue it.
 * Its probably better off an internal message generated from e.g. chat commands and then fed into
 * the ECS.
 */
data class Kill(
  override val playerId: Long,
  val entityId: EntityId
) : CMSG, InECSMessage
