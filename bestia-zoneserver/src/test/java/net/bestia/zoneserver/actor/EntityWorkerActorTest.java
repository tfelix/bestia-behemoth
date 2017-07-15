package net.bestia.zoneserver.actor;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;
import net.bestia.zoneserver.actor.entity.EntityWorkerActor;
import scala.concurrent.duration.Duration;

public class EntityWorkerActorTest {

	static ActorSystem system;

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
				final Props props = Props.create(EntityWorkerActor.class);
				final ActorRef subject = system.actorOf(props);

				// can also use JavaTestKit “from the outside”
				final TestKit probe = new TestKit(system);
				// “inject” the probe by passing it to the test subject
				// like a real resource would be passed in production
				subject.tell(probe.getRef(), getRef());
				// await the correct response
				expectMsg(duration("1 second"), "done");

				// the run() method needs to finish within 3 seconds
				within(duration("3 seconds"), () -> {
					subject.tell("hello", getRef());

					// This is a demo: would normally use expectMsgEquals().
					// Wait time is bounded by 3-second deadline above.
					awaitCond(probe::msgAvailable);

					// response must have been enqueued to us before probe
					expectMsg(Duration.Zero(), "world");
					// check that the probe we injected earlier got the msg
					probe.expectMsg(Duration.Zero(), "hello");
					Assert.assertEquals(getRef(), probe.getLastSender());

					// Will wait for the rest of the 3 seconds
					expectNoMsg();
					return null;
				});
			}
		};
	}
}
