package net.bestia.webserver.bestia;

import org.atmosphere.config.service.AtmosphereInterceptorService;
import org.atmosphere.cpr.Action;
import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.cpr.AtmosphereInterceptor;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResource;

@AtmosphereInterceptorService
public class BestiaInterceptor implements AtmosphereInterceptor {

	@Override
	public void configure(AtmosphereConfig config) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Action inspect(AtmosphereResource r) {
		AtmosphereRequest req = r.getRequest();

		final String token = req.getHeader("token");
		
		if (token == null || !token.equals("test123")) {
            return Action.CANCELLED;                   
        } else {
            return Action.CONTINUE;                   
        }
	}

	@Override
	public void postInspect(AtmosphereResource r) {
		// TODO Auto-generated method stub
		
	}

}
