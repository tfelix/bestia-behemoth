package net.bestia.zoneserver.actor;

import net.bestia.messages.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;

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
  public void sendToActiveClientsInRange(long entityIdWithPosition, @NotNull Serializable message) {
    LOG.debug("sendToActiveClientsInRange: {}", message);
    final ClientsInRangeEnvelope inRangeEnvelope = new ClientsInRangeEnvelope(entityIdWithPosition, message);
    postmaster.tell(inRangeEnvelope, ActorRef.noSender());
  }

  @Override
  public void sendToEntity(long entityId, @NotNull Serializable message) {
    LOG.debug("sendToEntity: {}", message);
    final EntityMessageEnvelope entityEnvelope = new EntityMessageEnvelope(entityId, message);
    postmaster.tell(entityEnvelope, ActorRef.noSender());
  }
}
