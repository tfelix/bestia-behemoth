package net.bestia.zoneserver.game.worker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ScriptInitWorker implements Runnable {
	
	private static final Logger log = LogManager.getLogger(ScriptInitWorker.class);
	
	public ScriptInitWorker() {
		
	}

	@Override
	public void run() {
		log.info("Initializing: Scripts...");
		
		// TODO Scripte parsen und initialisieren.
		
		log.info("Finished: Scripts.");
		
	}

}
