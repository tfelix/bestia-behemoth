package net.bestia.zone.entity.ecs

import net.bestia.bnet.proto.EntityFxComponentSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG

data class EntityFxSMSG(
  override val entityId: Long,
  val kind: EntityFx.Kind,
  val durationMs: Int,
) : EntitySMSG {
  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val fx = EntityFxComponentSMSGProto.EntityFxComponentSMSG.newBuilder()
      .setKind(kind.toProto())
      .setDurationMs(durationMs)

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setFx(fx))
  }

  private fun EntityFx.Kind.toProto(): EntityFxComponentSMSGProto.EntityFxKind =
    when (this) {
      EntityFx.Kind.WING_VANISH -> EntityFxComponentSMSGProto.EntityFxKind.WING_VANISH
      EntityFx.Kind.WING_APPEAR -> EntityFxComponentSMSGProto.EntityFxKind.WING_APPEAR
      EntityFx.Kind.HOME_VANISH -> EntityFxComponentSMSGProto.EntityFxKind.HOME_VANISH
      EntityFx.Kind.HOME_APPEAR -> EntityFxComponentSMSGProto.EntityFxKind.HOME_APPEAR
    }
}
