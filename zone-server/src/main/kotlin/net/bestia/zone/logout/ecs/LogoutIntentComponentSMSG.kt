package net.bestia.zone.logout.ecs

import net.bestia.bnet.proto.LogoutIntentSmsgProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG

/**
 * Owner-only sync of a pending logout countdown on the master entity. Produced by
 * [LogoutIntent.toEntityMessage]; re-sent periodically so the client countdown stays corrected.
 */
data class LogoutIntentComponentSMSG(
  override val entityId: Long,
  val remainingSeconds: Float,
  val removed: Boolean = false
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val proto = LogoutIntentSmsgProto.LogoutIntentSMSG.newBuilder()
      .setRemainingSeconds(remainingSeconds)
      .setRemoved(removed)
      .build()

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setLogoutIntent(proto))
  }
}
