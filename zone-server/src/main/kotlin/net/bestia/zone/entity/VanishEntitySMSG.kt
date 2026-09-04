package net.bestia.zone.entity

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.VanishEntitySmsgProto
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.util.EntityId

data class VanishEntitySMSG(
  override val entityId: EntityId,
  val kind: VanishKind
) : EntitySMSG {

  enum class VanishKind {
    /** Removed from the world for a reason the client is not told. */
    GONE,

    /** Killed, so a death animation is worth playing. */
    DEATH,

    /**
     * Still alive, just no longer in view - the chunk it stands in has left this client's subscription.
     *
     * Distinct from [GONE] because it must not play a send-off: an entity pacing a chunk boundary would
     * otherwise fade out and back repeatedly, and the client has to be free to drop the node at once.
     */
    OUT_OF_SIGHT
  }

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val kind = when (kind) {
      VanishKind.GONE -> VanishEntitySmsgProto.VanishKind.GONE
      VanishKind.DEATH -> VanishEntitySmsgProto.VanishKind.DEATH
      VanishKind.OUT_OF_SIGHT -> VanishEntitySmsgProto.VanishKind.OUT_OF_SIGHT
    }

    val vanishMsg = VanishEntitySmsgProto.VanishEntitySMSG.newBuilder()
      .setEntityId(entityId)
      .setKind(kind)

    return EnvelopeProto.Envelope.newBuilder()
      .setVanishEntity(vanishMsg)
      .build()
  }
}