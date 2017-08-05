package net.bestia.zoneserver.actor;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import net.bestia.zoneserver.actor.BRouterActor.RedirectMessage;

@RunWith(SpringRunner.class)
@SpringBootTest
@Import(ActorTestConfig.class)
public class ActorTestTest {
	
	@Autowired
	private ActorSystem system;
	
	@Test
	public void spring_test() {
		TestKit routee = new TestKit(system);
		
		ActorRef router = SpringExtension.actorOf(system, BRouterActor.class);
		
		BRouterActor.RedirectMessage reqMsg = new RedirectMessage(String.class, routee.getRef());
		
		router.tell("Test", routee.getRef());
		
		// Redirect
		router.tell(reqMsg, routee.getRef());
		
		router.tell("Test", ActorRef.noSender());
		
		String msg = routee.expectMsgClass(String.class);
		Assert.assertEquals("Test", msg);
	}

}
