package net.bestia.zoneserver.actor.entity;

import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import akka.actor.ActorIdentity;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Identify;
import akka.actor.Props;
import akka.pattern.AskableActorSelection;
import akka.testkit.javadsl.TestKit;
import akka.util.Timeout;
import net.bestia.server.AkkaCluster;
import net.bestia.zoneserver.TestZoneConfiguration;
import scala.concurrent.Await;
import scala.concurrent.Future;

@SpringBootTest
@Import(TestZoneConfiguration.class)
public class EntityWorkerActorTest {

	private static ActorSystem system;

	private final static long ENTITY_ID = 1;

	@BeforeClass
	public static void setup() {
		system = ActorSystem.create();
	}

	@AfterClass
	public static void teardown() {
		TestKit.shutdownActorSystem(system);
		system = null;
	}

	@Test
	public void testIt() {
		/*
		 * Wrap the whole test procedure within a testkit constructor if you
		 * want to receive actor replies or use Within(), etc.
		 */
		new TestKit(system) {
			{
				final Props props = Props.create(EntityManagerActor.class);
				final ActorRef subject = system.actorOf(props);

				// “inject” the probe by passing it to the test subject
				// like a real resource would be passed in production
				subject.tell(ENTITY_ID, getRef());

				// the run() method needs to finish within 3 seconds
				within(duration("3 seconds"), () -> {

					final String name = AkkaCluster.getNodeName(EntityActor.getActorName(ENTITY_ID));
					ActorSelection entityActor = system.actorSelection(name);
					AskableActorSelection asker = new AskableActorSelection(entityActor);
					final Timeout timeout = new Timeout(5, TimeUnit.SECONDS);
					Future<Object> fut = asker.ask(new Identify(1), timeout);
					try {
						ActorIdentity ident = (ActorIdentity) Await.result(fut, timeout.duration());
						ActorRef ref = ident.ref().get();
						Assert.assertNotNull(ref);
					} catch (Exception ex) {
						Assert.fail();
					}

					return null;
				});
			}
		};
	}
}
