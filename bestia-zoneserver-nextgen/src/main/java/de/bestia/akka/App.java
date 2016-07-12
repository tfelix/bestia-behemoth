package de.bestia.akka;

import java.util.concurrent.Future;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Terminated;
import de.bestia.akka.actor.ZoneRouter;
import de.bestia.akka.message.InputMessage;

public class App {

	public static void main(String[] args) {
		
		// Create the Akka system.
		final ActorSystem system = ActorSystem.create("bestia");
		
		final ActorRef master = system.actorOf(ZoneRouter.props());
		
		// Send three messages.
		InputMessage msg1 = new InputMessage(1, "Hello World.");
		InputMessage msg2 = new InputMessage(2, "Bla bla.");
		InputMessage msg3 = new InputMessage(1337, "Wrong id.");
		
		master.tell(msg1, ActorRef.noSender());
		master.tell(msg2, ActorRef.noSender());
		master.tell(msg3, ActorRef.noSender());
		
		try {
			Thread.sleep(8000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		master.tell(PoisonPill.getInstance(), ActorRef.noSender());
		
		system.terminate();		
	}

}
