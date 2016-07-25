package de.bestia.akka;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.bestia.akka.config.ClusterConfig;



public class ZoneserverApplication {
	
	private static final Logger LOG = LoggerFactory.getLogger(ClusterConfig.class);
	
	public static void main(String[] args) {
		
		Zoneserver zone = new Zoneserver();
		zone.run();
	    
		try {
			System.in.read();
		} catch (IOException e) {
			
		}
		LOG.info("Shutting down.");
	    //hazelcastInstance.shutdown();
	}

}
