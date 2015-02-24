package net.bestia.webserver.bestia;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atmosphere.config.service.Disconnect;
import org.atmosphere.config.service.ManagedService;
import org.atmosphere.config.service.Message;
import org.atmosphere.config.service.Ready;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceEvent;



@ManagedService(path = "/behemoth")
public class BestiaBehemoth {
   
	private final static Logger log = LogManager.getLogger(BestiaBehemoth.class);
	//private final ConcurrentLinkedQueue<String> uuids = new ConcurrentLinkedQueue<String>();

    @Ready
    public void onReady(final AtmosphereResource r) {
    	
    	log.info("Browser {} connected.", r.uuid());
    }

    @Disconnect
    public void onDisconnect(AtmosphereResourceEvent event) {
        if (event.isCancelled()) {
            log.info("Browser {} unexpectedly disconnected", event.getResource().uuid());
        } else if (event.isClosedByClient()) {
            log.info("Browser {} closed the connection", event.getResource().uuid());
        }
    }

    @Message()
    public String onMessage(String message) throws IOException {
        log.info("{} just send {}", message.toString());
        return message;
    }
}
