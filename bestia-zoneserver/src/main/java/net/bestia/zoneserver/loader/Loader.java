package net.bestia.zoneserver.loader;

import java.io.IOException;

/**
 * The Loaders are responsible for loading various stuff before the zone really can start. Usually these are assets etc.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface Loader {

	public void init() throws IOException;
	
}
