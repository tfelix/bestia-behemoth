package net.bestia.core.game.worker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ActorLogicWorker implements Runnable {
	
	private final static Logger log = LogManager.getLogger(ActorLogicWorker.class);
	private boolean isRunning = true;

	@Override
	public void run() {
		// TODO Auto-generated method stub

		while(isRunning) {
			//log.info("Logic Tick.");
			try {
				Thread.sleep(2500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	}
	
	public void stop() {
		isRunning = false;
	}

}
