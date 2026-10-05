package net.bestia.zone.prop.ecs.construction

import net.bestia.bnet.proto.ConstructionComponentSmsgProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG

/**
 * In-range broadcast of a construction site's progress, driving how far the finished art has faded in.
 * Produced by [ConstructionSite.toEntityMessage].
 *
 * Not [net.bestia.zone.battle.ecs.skill.CastingComponentSMSG], which a craft does reuse - see the proto.
 */
data class ConstructionComponentSMSG(
  override val entityId: Long,
  val remainingSeconds: Float,
  val totalSeconds: Float,
  val active: Boolean,
  val removed: Boolean = false
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val proto = ConstructionComponentSmsgProto.ConstructionComponentSMSG.newBuilder()
      .setRemainingSeconds(remainingSeconds)
      .setTotalSeconds(totalSeconds)
      .setActive(active)
      .setRemoved(removed)
      .build()

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setConstruction(proto))
  }
}
