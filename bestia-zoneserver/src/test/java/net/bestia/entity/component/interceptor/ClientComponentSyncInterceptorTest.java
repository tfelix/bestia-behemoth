package net.bestia.entity.component.interceptor;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.PlayerComponent;
import net.bestia.entity.component.PositionComponent;
import net.bestia.messages.MessageApi;
import net.bestia.messages.entity.ComponentInstallMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ClientComponentSyncInterceptorTest {

  @Mock
  private MessageApi msgApi;

  @Mock
  private EntityService entityService;

  @Mock
  private Entity entity;

  private ClientComponentSyncInterceptor interceptor;

  private long entityId = 100;
  private long ownerId = 500;


  private PlayerComponent playerComponent = new PlayerComponent(1);
  private PositionComponent positionComponent = new PositionComponent(2);

  public ClientComponentSyncInterceptorTest() {
    playerComponent.setOwnerAccountId(ownerId);
  }

  @Before
  public void setup() {
    positionComponent.setEntityId(entityId);
    interceptor = new ClientComponentSyncInterceptor(msgApi);

    when(entityService.getComponent(entity, PlayerComponent.class))
            .thenReturn(Optional.of(playerComponent));
  }

  private void checkSendToClient() {
    verify(msgApi).sendToEntity(eq(entityId), any(ComponentInstallMessage.class));
    verify(msgApi).sendToClient(eq(ownerId), any());
  }

  @Test
  public void triggerCreateAction_anyComponent_sendMessage() {
    interceptor.triggerCreateAction(entityService, entity, positionComponent);

    checkSendToClient();
  }

  @Test
  public void triggerUpdateActionn_anyComponent_sendMessage() {
    interceptor.triggerUpdateAction(entityService, entity, positionComponent);

    checkSendToClient();
  }

  @Test
  public void triggerDeleteActionn_anyComponent_sendMessage() {
    interceptor.triggerDeleteAction(entityService, entity, positionComponent);

    checkSendToClient();
  }
}