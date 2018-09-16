package net.bestia.zoneserver.entity.component.interceptor

import net.bestia.entity.component.interceptor.BaseComponentInterceptor
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.EntityService
import net.bestia.zoneserver.entity.component.Component
import net.bestia.messages.entity.ComponentIdEnvelope
import net.bestia.messages.entity.EntityEnvelope

/**
 * This interceptor will check if the component was annotated to notify certain actors
 * if the component value has changed. The actor will then get notified.
 */
class ActorUpdateComponentInterceptor(
        private val msgApi: MessageApi
) : BaseComponentInterceptor<Component>(Component::class.java) {

  override fun onDeleteAction(entityService: EntityService, entity: Entity, comp: Component) {
    val syncActor = comp.javaClass.getAnnotation(ActorSync::class.java)
    if (dontUpdateActor(syncActor)) {
      return
    }
    updateActorComponent(entity, ComponentDeletedMessage(comp.id))
  }

  override fun onUpdateAction(entityService: EntityService, entity: Entity, comp: Component) {
    val syncActor = comp.javaClass.getAnnotation(ActorSync::class.java)
    if (dontUpdateActor(syncActor)) {
      return
    }
    updateActorComponent(entity, ComponentUpdateMessage(comp.id))
  }

  override fun onCreateAction(entityService: EntityService, entity: Entity, comp: Component) {
    val syncActor = comp.javaClass.getAnnotation(ActorSync::class.java)
    if (dontUpdateActor(syncActor)) {
      return
    }
    updateActorComponent(entity, ComponentCreatedMessage(comp.id))
  }

  private fun updateActorComponent(entity: Entity, msg: ComponentChangedMessage) {
    val compEnv = ComponentIdEnvelope(msg.componentId, msg)
    val entityEnv = EntityEnvelope(entity.id, compEnv)
    msgApi.send(entityEnv)
  }

  private fun dontUpdateActor(syncActor: ActorSync?): Boolean {
    return syncActor == null || !syncActor.updateActorOnChange
  }
}
