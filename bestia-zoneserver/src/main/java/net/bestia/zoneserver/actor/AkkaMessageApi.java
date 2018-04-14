package net.bestia.zoneserver.actor;

import akka.actor.ActorRef;
import net.bestia.messages.MessageApi;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AkkaMessageApi implements MessageApi {

  private final static Logger LOG = LoggerFactory.getLogger(AkkaMessageApi.class);

  private ActorRef postmaster;

  @Override
  public void setPostmaster(@NotNull ActorRef postmaster) {
    this.postmaster = postmaster;
  }

  @Override
  public void send(@NotNull Object message) {
    LOG.debug("sendig: {}", message);
    postmaster.tell(message, ActorRef.noSender());
  }
}
