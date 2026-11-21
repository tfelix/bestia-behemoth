package net.bestia.zone.item

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.StorageOpenedSMSGProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.EntityId

/** Opens a slot storage, such as a chest, in the player's inventory. Its contents follow as the entity's bag. */
data class StorageOpenedSMSG(
  val entityId: EntityId,
  val slots: Int,
  val kind: Kind,
) : SMSG {

  /** In the proto's order. */
  enum class Kind { CHEST }

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val opened = StorageOpenedSMSGProto.StorageOpenedSMSG.newBuilder()
      .setEntityId(entityId)
      .setSlots(slots)
      .setKind(StorageOpenedSMSGProto.StorageKind.forNumber(kind.ordinal))
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setStorageOpened(opened)
      .build()
  }
}
