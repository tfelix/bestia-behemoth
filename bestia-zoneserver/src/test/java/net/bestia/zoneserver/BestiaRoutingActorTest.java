package net.bestia.zoneserver;

import java.util.Arrays;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import akka.actor.ActorSystem;
import net.bestia.messages.Message;
import net.bestia.messages.MessageId;
import net.bestia.zoneserver.actor.BestiaRoutingActor;

public class BestiaRoutingActorTest {

	private ActorSystem system;
	
	private static class TestMessage extends Message {
		
		public final static String MESSAGE_ID = "testmessage";

		@Override
		public String getMessageId() {
			return MESSAGE_ID;
		}
		
	}

	private static class TestBestiaRoutingActor extends BestiaRoutingActor {

		public void testNull() {
			addActor(null);
		}

		@Override
		protected List<Class<? extends Message>> getHandledMessages() {
			return Arrays.asList(TestMessage.class);
		}

		@Override
		protected void handleMessage(MessageId msg) {
			// TODO 
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
