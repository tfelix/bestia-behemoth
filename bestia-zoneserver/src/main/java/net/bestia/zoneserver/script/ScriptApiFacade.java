package net.bestia.zoneserver.script;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bundles all kind of services to provide an extensive script API. This API is
 * bound to every script execution and can be used in order to interact with the
 * bestia server.
 * 
 * @author Thomas Felix
 *
 */
public class ScriptApiFacade implements ScriptApi {
	
	private static final Logger SCRIPT_LOG = LoggerFactory.getLogger("script");

	@Override
	public void info(String text) {
		SCRIPT_LOG.info(text);
	}

	@Override
	public void debug(String text) {
		SCRIPT_LOG.debug(text);
	}

}
