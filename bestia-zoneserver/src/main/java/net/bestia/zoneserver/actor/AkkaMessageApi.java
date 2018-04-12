package net.bestia.zoneserver.actor;

import akka.actor.ActorRef;
import net.bestia.messages.client.ClientToMessageEnvelope;
import net.bestia.messages.entity.EntityMessageEnvelope;
import net.bestia.messages.MessageApi;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

public class AkkaMessageApi implements MessageApi {

  private final static Logger LOG = LoggerFactory.getLogger(AkkaMessageApi.class);

  private ActorRef postmaster;

  @Override
  public void setPostmaster(@NotNull ActorRef postmaster) {
    this.postmaster = postmaster;
  }

  @Override
  public void sendToClient(long clientAccountId, @NotNull Serializable message) {
    LOG.debug("sendToClient: {}", message);
    final ClientToMessageEnvelope clientEnvelope = new ClientToMessageEnvelope(clientAccountId, message);
    postmaster.tell(clientEnvelope, ActorRef.noSender());
  }

  @Override
  public void sendToEntity(long entityId, @NotNull Serializable message) {
    LOG.debug("sendToEntity: {}", message);
    final EntityMessageEnvelope entityEnvelope = new EntityMessageEnvelope(entityId, message);
    postmaster.tell(entityEnvelope, ActorRef.noSender());
  }
}
