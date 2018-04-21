package net.bestia.zoneserver.actor.zone;

import akka.actor.AbstractActor;
import akka.japi.pf.FI;
import akka.japi.pf.ReceiveBuilder;
import net.bestia.messages.Envelope;
import net.bestia.zoneserver.actor.zone.ClientMessageActor.RedirectMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientMessageDigestActor extends AbstractActor {

  private class Tuple<P> {
    final Class<P> type;
    final FI.UnitApply<P> apply;

    Tuple(Class<P> type, FI.UnitApply<P> apply) {
      this.type = type;
      this.apply = apply;
    }

    void apply(ReceiveBuilder builder) {
      builder.match(type, apply);
    }
  }

  protected class Config {
    private List<Tuple<?>> tuples = new ArrayList<>();
    private Map<Class, FI.UnitApply2> envelopeCallbacks = new HashMap<>();
    private boolean isConfigured = false;

    private List<Tuple<?>> getTuples() {
      return tuples;
    }

    public <P> void match(Class<P> type, FI.UnitApply<P> apply) {
      isConfigured = true;
      tuples.add(new Tuple<>(type, apply));
    }

    public <E extends Envelope, P> void matchEnvelope(Class<P> type, FI.UnitApply2<P, E> apply) {
      isConfigured = true;
      envelopeCallbacks.put(type, apply);
    }
  }

  protected final Config redirectConfig = new Config();

  @Override
  public void preStart() throws Exception {
    checkConfigPresent();

    redirectConfig.getTuples().forEach(t -> {
      final RedirectMessage msg = RedirectMessage.get(t.type);
      context().parent().tell(msg, getSelf());
    });
  }

  @Override
  public Receive createReceive() {
    checkConfigPresent();

    final ReceiveBuilder builder = receiveBuilder();
    redirectConfig.getTuples().forEach(t -> t.apply(builder));
    builder.match(Envelope.class, this::handleEnvelope);
    return builder.build();
  }

  private void handleEnvelope(Envelope msg) {
    final FI.UnitApply2 fn = redirectConfig.envelopeCallbacks.get(msg.getContent().getClass());
    if(fn != null) {
      try {
        //noinspection unchecked
        fn.apply(msg.getContent(), msg);
      } catch (Exception e) {

      }
    }
  }

  private void checkConfigPresent() {
    if (!redirectConfig.isConfigured) {
      throw new IllegalStateException("setMessageHandler was not called in the childs ctor!");
    }
  }
}
