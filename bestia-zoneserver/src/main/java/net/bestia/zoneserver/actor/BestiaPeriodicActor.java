package net.bestia.zoneserver.actor;

import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import scala.concurrent.duration.Duration;

/**
 * This actor supports the automatic injection of dependencies aswell as an
 * periodic tick system. One can change the next tick call by calling
 * setInterval().
 * 
 * @author Thomas
 *
 */
@Component
@Scope("prototype")
public abstract class BestiaPeriodicActor extends BestiaActor {

	private static final String TICK_MSG = "net.bestia.TICK_MSG";

	private int intervalDuration;

	public BestiaPeriodicActor() {
		super();
	}

	/**
	 * Sets the duration between the invocation ticks. Must be bigger or equal
	 * 1. Its unit is ms.
	 * 
	 * @param duration
	 *            Duration until the next tick in ms.
	 */
	protected void setIntervalDuration(int duration) {
		if (duration < 1) {
			throw new IllegalArgumentException("Duration must be at least 1.");
		}

		this.intervalDuration = duration;
	}

	/**
	 * This method is called during each tick of the actor. It must be
	 * overwritten by child implementations.
	 */
	protected abstract void onTick();

	/**
	 * Handles a normal message which might arrive as well.
	 * 
	 * @param message
	 *            The message send to this actor.
	 */
	protected abstract void handleMessage(Object message) throws Exception;

	@Override
	public void onReceive(Object message) throws Throwable {
		// Check if we received a tick message.
		if (message.equals(TICK_MSG)) {
			onTick();
			setupTicker();
		} else {
			handleMessage(message);
		}
	}

	/**
	 * Starts a new tick event for the next invocation of this actor.
	 */
	private void setupTicker() {
		// send another periodic tick after the specified delay
		getContext().system().scheduler().scheduleOnce(
				Duration.create(intervalDuration, TimeUnit.MILLISECONDS),
				getSelf(), TICK_MSG, getContext().dispatcher(), null);
	}

}