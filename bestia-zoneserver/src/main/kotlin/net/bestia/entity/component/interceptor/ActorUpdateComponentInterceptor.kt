package net.bestia.entity.component.interceptor

import net.bestia.entity.Entity
import net.bestia.entity.EntityService
import net.bestia.entity.component.ActorSync
import net.bestia.entity.component.Component
import net.bestia.messages.*
import net.bestia.messages.entity.ComponentEnvelope
import net.bestia.messages.entity.ToEntityEnvelope

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
    val compEnv = ComponentEnvelope(msg.componentId, msg)
    val entityEnv = ToEntityEnvelope(entity.id, compEnv)
    msgApi.send(entityEnv)
  }

  private fun dontUpdateActor(syncActor: ActorSync?): Boolean {
    return syncActor == null || !syncActor.updateActorOnChange
  }
}
