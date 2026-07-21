package net.bestia.zone.ecs.logout

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.LogoutIntentSmsgProto
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

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val proto = LogoutIntentSmsgProto.LogoutIntentSMSG.newBuilder()
      .setEntityId(entityId)
      .setRemainingSeconds(remainingSeconds)
      .setRemoved(removed)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setCompLogoutIntent(proto)
      .build()
  }
}
