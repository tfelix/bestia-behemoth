package net.bestia.zoneserver.actor;

import java.util.concurrent.TimeUnit;

import akka.actor.Cancellable;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import scala.concurrent.duration.Duration;

/**
 * These actors self terminate from the beginning if there is no other task
 * received for them after 5 minutes.
 * 
 * @author Thomas Felix
 *
 */
public abstract class BestiaPeriodicTerminatingActor extends BestiaPeriodicActor {
	
	private final LoggingAdapter LOG = Logging.getLogger(getContext().system(), this);
	
	private static final String KILL_MESSAGE = "killmyselftofeelalive";
	private Cancellable killTicker;
	
	public BestiaPeriodicTerminatingActor() {
		
		startKillInterval();
	}

	private void startKillInterval() {
		if(killTicker != null) {
			killTicker.cancel();
			killTicker = null;
		}
		
		final int KILL_COUNTDOWN = 3 * 60 * 1000;
		
		// send another periodic tick after the specified delay
		killTicker = getContext().system().scheduler().scheduleOnce(
				Duration.create(KILL_COUNTDOWN, TimeUnit.MILLISECONDS),
				getSelf(), KILL_MESSAGE, getContext().dispatcher(), null);
	}
	
	private void stopKillInterval() {
		
		if(killTicker == null) {
			return;
		}
		
		LOG.debug("Actor {} has received a external message. Stopping kill timer now.", getSelf().path());
		
		killTicker.cancel();
		killTicker = null;
	}
	
	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.matchEquals(KILL_MESSAGE, m -> {
					LOG.debug("Actor {} has not received any external message. Killing myself now.", getSelf().path());
					context().stop(getSelf());
				})
				.matchEquals(TICK_MSG, m -> stopKillInterval())
				.matchAny(apply)
				.build();
	}
}
