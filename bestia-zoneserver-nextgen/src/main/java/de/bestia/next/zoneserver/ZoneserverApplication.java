package de.bestia.next.zoneserver;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import de.bestia.next.zoneserver.actor.ZoneRouter;
import de.bestia.next.zoneserver.component.ClusterConfig;
import de.bestia.next.zoneserver.message.InputMessage;

@SpringBootApplication
public class ZoneserverApplication implements CommandLineRunner {

	private static final Logger LOG = LoggerFactory.getLogger(ClusterConfig.class);

	public static void main(String[] args) throws Exception {
		SpringApplication.run(ZoneserverApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
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

		Zoneserver zone = new Zoneserver();
		zone.run();

		try {
			System.in.read();
		} catch (IOException e) {

		}
		LOG.info("Shutting down.");
		// hazelcastInstance.shutdown();
	}

}