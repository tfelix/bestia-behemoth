package net.bestia.zoneserver;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.ActorSystem;
import akka.actor.Props;
import net.bestia.server.AkkaCluster;
import net.bestia.zoneserver.actor.BestiaRoutingActor;

public class BestiaRoutingActorTest {

	private ActorSystem system;

	private static class TestBestiaRoutingActor extends BestiaRoutingActor {

		public void testNull() {
			addActor(null);
		}
	}

	@BeforeClass
	public void setup() {
		this.system = ActorSystem.create("akkaTest");
	}

	@AfterClass
	public void teardown() {
		try {
			this.system.terminate().wait();
		} catch (InterruptedException e) {
			// no op.
		}
	}

	public void addActor_null_throws() {

	}

	public void addActor_actor_routesMessage() {

	}

	private ActorSystem getSystem() {
		final ActorSystem system = ActorSystem.create("akkaTest");
		return system;
	}
}
