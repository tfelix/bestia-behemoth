package net.bestia.zone.message.entity

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.VanishEntitySmsgProto
import net.bestia.zone.util.EntityId

data class VanishEntitySMSG(
  override val entityId: EntityId,
  val kind: VanishKind
) : EntitySMSG {

  enum class VanishKind {
    /**
     * Entity is just gone.
     */
    GONE,

    /**
     * Play death animation if present.
     */
    DEATH
  }

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val kind = when (kind) {
      VanishKind.GONE -> VanishEntitySmsgProto.VanishKind.GONE
      VanishKind.DEATH -> VanishEntitySmsgProto.VanishKind.DEATH
    }

    val vanishMsg = VanishEntitySmsgProto.VanishEntitySMSG.newBuilder()
      .setEntityId(entityId)
      .setKind(kind)

    return EnvelopeProto.Envelope.newBuilder()
      .setVanishEntity(vanishMsg)
      .build()
  }
}