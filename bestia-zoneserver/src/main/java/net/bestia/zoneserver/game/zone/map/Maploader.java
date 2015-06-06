package net.bestia.zoneserver.game.zone.map;

import java.io.IOException;

/**
 * Interface for providing a map loading interface. Maps will use a Maploader to
 * get all the data out of map files in order to load the appropriate file.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface Maploader {

	/**
	 * Loads the wrapped datasource into a mapbuilder which then creates a
	 * {@link Map}.
	 * 
	 * @param builder
	 * @throws IOException
	 *             If the loading and parsing of the datasource fails.
	 */
	public void loadMap(Map.Mapbuilder builder) throws IOException;

}
