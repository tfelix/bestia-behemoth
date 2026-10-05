package net.bestia.zone.battle.ecs.skill

import net.bestia.bnet.proto.CastingComponentSmsgProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG

/**
 * In-range broadcast of a running cast, driving the cast bar above the entity's head. Produced by
 * [Casting.toEntityMessage]; re-sent while the cast runs so the client stays corrected.
 */
data class CastingComponentSMSG(
  override val entityId: Long,
  val remainingSeconds: Float,
  val totalSeconds: Float,
  val removed: Boolean = false
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val proto = CastingComponentSmsgProto.CastingComponentSMSG.newBuilder()
      .setRemainingSeconds(remainingSeconds)
      .setTotalSeconds(totalSeconds)
      .setRemoved(removed)
      .build()

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setCasting(proto))
  }
}
