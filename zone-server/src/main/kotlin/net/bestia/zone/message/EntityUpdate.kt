package net.bestia.zone.message

import com.google.protobuf.ByteString
import net.bestia.bnet.proto.StateBatchSmsgProto

/**
 * The messages about one entity in a [StateBatchSMSG]. Immutable, so one instance can go into the batch of every
 * account that sees the entity, and it is serialised only once for all of them.
 */
class EntityUpdate(
  val entityId: Long,
  val messages: List<EntitySMSG>,
) {

  val proto: StateBatchSmsgProto.EntityUpdate by lazy {
    val update = StateBatchSmsgProto.EntityUpdate.newBuilder().setEntityId(entityId)
    messages.forEach { it.writeTo(update) }
    update.build()
  }

  val encoded: ByteString by lazy { proto.toByteString() }
}
