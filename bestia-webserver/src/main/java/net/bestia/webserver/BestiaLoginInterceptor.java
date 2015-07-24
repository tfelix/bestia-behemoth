package net.bestia.webserver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atmosphere.cpr.Action;
import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.cpr.AtmosphereInterceptor;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResource;

public class BestiaLoginInterceptor implements AtmosphereInterceptor {

	private final static Logger log = LogManager
			.getLogger(BestiaLoginInterceptor.class);

	@Override
	public void configure(AtmosphereConfig config) {

	}

	@Override
	public void destroy() {

	}

	@Override
	public Action inspect(AtmosphereResource r) {
		log.debug("Intercepting connection. Checking for login: "
				+ r.toString());
		AtmosphereRequest req = r.getRequest();

		if (req.getHeader("X-Bestia-Access-Token") == null) {
			return Action.CANCELLED;

		} else {
			
			// Send a login check message to the login server.
			
			return Action.CONTINUE;
		}
	}

	@Override
	public void postInspect(AtmosphereResource arg0) {

	}

}
