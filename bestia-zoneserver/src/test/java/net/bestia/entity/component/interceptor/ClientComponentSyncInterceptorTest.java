package net.bestia.entity.component.interceptor;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.PositionComponent;
import net.bestia.messages.MessageApi;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ClientComponentSyncInterceptorTest {

  @Mock
  private MessageApi msgApi;

  @Mock
  private EntityService entityService;

  @Mock
  private Entity entity;

  private ClientComponentSyncInterceptor interceptor;

  private PositionComponent positionComponent = new PositionComponent(1);

  @Before
  public void setup() {

    interceptor = new ClientComponentSyncInterceptor(msgApi);
  }

  @Test
  public void triggerCreateAction_anyComponent_sendMessage() {

    interceptor.triggerCreateAction(entityService, entity, positionComponent);
    Assert.fail("TODO");
    // Mockito.verify(msgApi).sendToActiveClientsInRange(Mockito.any(EntityComponentEnvelope.class));
  }

  @Test
  public void triggerUpdateActionn_anyComponent_sendMessage() {

    interceptor.triggerUpdateAction(entityService, entity, positionComponent);
    // Mockito.verify(msgApi).sendToActiveClientsInRange(Mockito.any(EntityComponentEnvelope.class));
  }

  @Test
  public void triggerDeleteActionn_anyComponent_sendMessage() {

    interceptor.triggerDeleteAction(entityService, entity, positionComponent);
    // Mockito.verify(msgApi).sendToActiveClientsInRange(Mockito.any(EntityComponentDeleteMessage.class));
  }
}
