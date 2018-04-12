package net.bestia.entity.component.interceptor

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import net.bestia.entity.Entity
import net.bestia.entity.EntityService
import net.bestia.entity.component.ClientSync
import net.bestia.entity.component.Component
import net.bestia.entity.component.EntityComponentSyncMessageFactory
import net.bestia.entity.component.receiver.ActorReceiver
import net.bestia.entity.component.receiver.ClientReceiver
import net.bestia.messages.MessageApi
import net.bestia.messages.entity.EntityComponentSyncMessage
import org.springframework.context.ApplicationContext

private val LOG = KotlinLogging.logger { }

/**
 * This component interceptor will test all components if a change will lead to
 * client notification or actor notification. How the change of the component is
 * determined is given by its annotations.
 *
 * @author Thomas Felix
 */
@org.springframework.stereotype.Component
class ClientSyncInterceptorEx(
        private val msgApi: MessageApi,
        private val applicationContext: ApplicationContext
) {
  private val mapper = ObjectMapper()

  fun triggerCreateAction(entityService: EntityService, entity: Entity, comp: Component) {
    LOG.debug("Component {} is created.", comp)
    performComponentSync(entityService, entity, comp)
  }

  fun triggerDeleteAction(entityService: EntityService, entity: Entity, comp: Component) {
    LOG.debug("Component {} is deleted.", comp)
    performComponentSync(entityService, entity, comp)
  }

  fun triggerUpdateAction(entityService: EntityService, entity: Entity, comp: Component) {
    LOG.debug("Component {} is updated.", comp)
    performComponentSync(entityService, entity, comp)
  }

  /**
   * Checks if the given [Component] should be synced towards the
   * clients.
   *
   * @param comp The component to possibly sync.
   */
  private fun performComponentSync(entityService: EntityService, entity: Entity, comp: Component) {

    val clientSync = comp.javaClass.getAnnotation(ClientSync::class.java) ?: return

    val receiverActors = mutableSetOf<ActorReceiver>()
    val receiverClients = mutableSetOf<ClientReceiver>()

    clientSync.directives.forEach {
      val gatherer = applicationContext.getBean(it.receiver.java)
      val condition = applicationContext.getBean(it.condition.java)
      val transform = applicationContext.getBean(it.transform.java)

      val receiver = gatherer.gatherReceiver(entity, comp, entityService)

      if (!condition.doSync(entity, comp, entityService)) {
        return
      }

      receiver.forEach {
        when (it) {
          is ActorReceiver -> receiverActors.add(it)
          is ClientReceiver -> receiverClients.add(it)
        }
      }

      val transformedComp = transform.transform(comp)
      // Das sollte nur an die clients gehen, Actoren können andere, bessere Messages bekommen
      val msg = envelopeForComponent(transformedComp)

      // TODO An alle empfänger senden
    }
  }

  private fun envelopeForComponent(component: Component): EntityComponentSyncMessage {
    return EntityComponentSyncMessage(0,
            component.entityId,
            componentName(component.javaClass),
            stringifyPayload(component),
            0)
  }

  private fun stringifyPayload(component: Component): String? {
    return try {
      mapper.writeValueAsString(component)
    } catch (e: JsonProcessingException) {
      LOG.error("Can not serialize component.", e)
      null
    }
  }

  /**
   * Helper for creating names sticking to the standard of Component names.
   *
   * @param clazz The class of the component to add to this system.
   * @return A legal name.
   */
  private fun componentName(clazz: Class<out Component>): String {
    return clazz.simpleName
            .toUpperCase()
            .replace("COMPONENT", "")
  }
}