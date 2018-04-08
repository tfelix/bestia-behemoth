package net.bestia.net.bestia.entity.component

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import net.bestia.entity.component.Component
import net.bestia.messages.entity.EntityComponentSyncMessage

private val LOG = KotlinLogging.logger {  }

/**
 * Transforms the components into messages usable by the clients for updating their data model.
 */
@org.springframework.stereotype.Component
class EntityComponentSyncMessageFactory {
  private val mapper = ObjectMapper()

  fun forComponent(component: Component): EntityComponentSyncMessage? {
    val entityId = component.entityId
    val componentName = componentName(component.javaClass)
    return try {
      val payload = mapper.writeValueAsString(component)
      EntityComponentSyncMessage(0L, entityId, componentName, payload, 0)
    } catch (e: JsonProcessingException) {
      LOG.error("Can not create JSON from component.", e)
      null
    }
  }

  fun forCustomComponentPayload(entityId: Long,
                                compClass: Class<out Component>,
                                payload: Any): EntityComponentSyncMessage? {
    val componentName = componentName(compClass)
    return try {
      val payloadStr = mapper.writeValueAsString(payload)
      EntityComponentSyncMessage(0L, entityId, componentName, payloadStr, 0)
    } catch (e: JsonProcessingException) {
      LOG.error("Can not create JSON from component.", e)
      null
    }

  }

  private fun componentName(clazz: Class<out Component>): String {
    return clazz.simpleName.toUpperCase().replace("COMPONENT", "")
  }
}
