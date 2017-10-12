package net.bestia.webserver.actor;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.HazelcastInstance;

import akka.actor.ActorSystem;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

/**
 * This runner will try to end the akka actor system gracefully.
 * 
 * @author Thomas Felix
 *
 */
public class ActorSystemTerminator implements Runnable {
	
	private final static Logger LOG = LoggerFactory.getLogger(ActorSystemTerminator.class);
	
	private boolean hasRun = false;
	private final ActorSystem system;
	private final HazelcastInstance hz;
	
	public ActorSystemTerminator(ActorSystem system, HazelcastInstance hz) {
		
		this.system = Objects.requireNonNull(system);
		this.hz = Objects.requireNonNull(hz);
	}

	public void run() {
		// Safety check if we where already terminated.
		if(hasRun) {
			return;
		}
		hasRun = true;
		
		LOG.info("Terminating the akka system and hazelcast.");
		
		// Shutdown Hazelcast.
		hz.shutdown();
		
		// exit JVM when ActorSystem has been terminated
		final Runnable exit = new Runnable() {
			@Override
			public void run() {
				System.exit(0);
			}
		};
		system.registerOnTermination(exit);

		// shut down ActorSystem
		system.terminate();

		// In case ActorSystem shutdown takes longer than 10 seconds,
		// exit the JVM forcefully anyway.
		// We must spawn a separate thread to not block current thread,
		// since that would have blocked the shutdown of the
		// ActorSystem.
		new Thread() {
			@Override
			public void run() {
				try {
					Await.ready(system.whenTerminated(), Duration.create(10, TimeUnit.SECONDS));
				} catch (Exception e) {
					LOG.warn("System has not gracefully terminated. Now killing.");
					System.exit(-1);
				}
			}
		}.start();
	}
}
