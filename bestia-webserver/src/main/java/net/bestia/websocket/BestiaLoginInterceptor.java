package net.bestia.websocket;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atmosphere.cpr.Action;
import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.cpr.AtmosphereInterceptor;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResource;

public class BestiaLoginInterceptor implements AtmosphereInterceptor {
	
	private final static Logger log = LogManager.getLogger(BestiaLoginInterceptor.class);

	@Override
	public void configure(AtmosphereConfig arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void destroy() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Action inspect(AtmosphereResource r) {
		log.debug("Intercepting connection. Checking for login: " + r.toString());
		AtmosphereRequest req = r.getRequest();
        if (req.getHeader("SomeSecurityToken") == null) {
        	//req.
            return Action.CANCELLED;
            
        } else {
            return Action.CONTINUE;                   
        }
	}

	@Override
	public void postInspect(AtmosphereResource arg0) {
		// TODO Auto-generated method stub
		
	}

}
