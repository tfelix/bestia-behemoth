package net.bestia.zone.entity.ecs

import net.bestia.bnet.proto.DeadComponentSmsgProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG

/**
 * Tells everyone in range that a player-owned entity is lying dead, and - with [removed] - that it is
 * back on its feet. Produced by [Dead.toEntityMessage].
 */
data class DeadComponentSMSG(
  override val entityId: Long,
  val removed: Boolean = false
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val proto = DeadComponentSmsgProto.DeadComponentSMSG.newBuilder()
      .setRemoved(removed)
      .build()

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setDead(proto))
  }
}
