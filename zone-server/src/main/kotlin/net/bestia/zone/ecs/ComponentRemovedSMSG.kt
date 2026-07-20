package net.bestia.zone.ecs

import net.bestia.bnet.proto.ComponentRemovedSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.EntitySMSG

/**
 * Generic "a synced component was removed from this entity" notification — the counterpart to a
 * component's [Dirtyable] update message. Emitted by [ZoneEngine] whenever a [RemovalNotifiable]
 * component is removed from a still-alive entity, and delivered to the same targets the component
 * synced to. Distinct from [net.bestia.zone.entity.VanishEntitySMSG], which covers the whole entity
 * disappearing.
 */
data class ComponentRemovedSMSG(
  override val entityId: Long,
  val component: RemovableComponentType
) : EntitySMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val proto = ComponentRemovedSmsgProto.ComponentRemovedSMSG.newBuilder()
      .setEntityId(entityId)
      .setComponent(component.toProto())
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setCompRemoved(proto)
      .build()
  }

  private fun RemovableComponentType.toProto(): ComponentRemovedSmsgProto.RemovableComponent =
    when (this) {
      RemovableComponentType.LOGOUT_INTENT -> ComponentRemovedSmsgProto.RemovableComponent.LOGOUT_INTENT
      RemovableComponentType.CASTING -> ComponentRemovedSmsgProto.RemovableComponent.CASTING
    }
}
