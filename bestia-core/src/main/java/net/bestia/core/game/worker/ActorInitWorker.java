package net.bestia.core.game.worker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ActorInitWorker implements Runnable {
	
	private static final Logger log = LogManager.getLogger(ActorInitWorker.class);
	
	public ActorInitWorker() {
		
	}

	@Override
	public void run() {
		log.info("Initializing: Actors...\n...");
		
		// TODO Scripte parsen und initialisieren.
		
		log.info("Finished: Actors.");
		
	}

}
