package net.bestia.zoneserver.actor.zone;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import akka.actor.Actor;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestActorRef;
import net.bestia.zoneserver.actor.SpringExtension;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class PingPongActorTest {

	@Autowired
	private PingPongActor pingPongActor;

	@Autowired
	private ActorSystem system;

	@Test
	public void ping_sendsPong() {
		TestActorRef<PingPongActor> ref = TestActorRef.create(system, springProps(PingPongActor.class));
		PingPongActor actor = ref.underlyingActor();
	}

	protected Props springProps(Class<? extends Actor> actorClass) {
		return SpringExtension.PROVIDER.get(system).props(actorClass);
	}

}
