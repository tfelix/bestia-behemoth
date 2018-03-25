package net.bestia.entity.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.bestia.messages.entity.EntityComponentSyncMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transforms the components into messages usable by the clients for updating their data model.
 */
@org.springframework.stereotype.Component
public class EntityComponentSyncMessageFactory {

  private final static Logger LOG = LoggerFactory.getLogger(EntityComponentSyncMessageFactory.class);
  private final ObjectMapper mapper = new ObjectMapper();

  public EntityComponentSyncMessage forComponent(Component component) {
    final long entityId = component.getEntityId();
    final String componentName = componentName(component.getClass());
    try {
      final String payload = mapper.writeValueAsString(component);
      return new EntityComponentSyncMessage(0L, entityId, componentName, payload, 0);
    } catch (JsonProcessingException e) {
      LOG.error("Can not create JSON from component.", e);
      return null;
    }
  }

  public EntityComponentSyncMessage forCustomComponentPayload(long entityId,
                                                              Class<? extends Component> compClass,
                                                              Object payload) {
    final String componentName = componentName(compClass);
    try {
      final String payloadStr = mapper.writeValueAsString(payload);
      return new EntityComponentSyncMessage(0L, entityId, componentName, payloadStr, 0);
    } catch (JsonProcessingException e) {
      LOG.error("Can not create JSON from component.", e);
      return null;
    }
  }

  private String componentName(Class<? extends Component> clazz) {
    return clazz.getSimpleName().toUpperCase().replace("COMPONENT", "");
  }
}
