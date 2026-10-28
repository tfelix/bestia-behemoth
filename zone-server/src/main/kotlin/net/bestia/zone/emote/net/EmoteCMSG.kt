package net.bestia.zone.emote.net

import net.bestia.bnet.proto.EmoteCMSGProto
import net.bestia.zone.entity.ecs.Animation
import net.bestia.zone.entity.ecs.AnimationSMSG.Companion.fromProto
import net.bestia.zone.message.CMSG

/** @property kind null when the client sent a value this server does not know. */
data class EmoteCMSG(
  override val playerId: Long,
  val kind: Animation.AnimationKind?,
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, emote: EmoteCMSGProto.EmoteCMSG): EmoteCMSG {
      return EmoteCMSG(accountId, emote.kind.fromProto())
    }
  }
}
