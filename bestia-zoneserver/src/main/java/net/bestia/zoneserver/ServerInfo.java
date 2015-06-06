package net.bestia.zoneserver;

import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Holds immutable information about a bestia zoneserver.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ServerInfo {

	static class ServerInfoBuilder {
		private URL url;
		private HashSet<String> zones = new HashSet<String>();

		public ServerInfo build() {
			return new ServerInfo(this);
		}

		public ServerInfoBuilder setUrl(URL url) {
			this.url = url;
			return this;
		}
		
		public ServerInfoBuilder addZone(String zone) {
			zones.add(zone);
			return this;
		}
	}

	private final URL url;
	/**
	 * Set of zones which this server handles. The zones are the map_db_names.
	 */
	private final Set<String> responsibleZones;

	private ServerInfo(ServerInfoBuilder builder) {
		this.url = builder.url;
		this.responsibleZones = Collections.unmodifiableSet(builder.zones);
	}

	/**
	 * Returns the url of this server.
	 * 
	 * @return
	 */
	public URL getURL() {
		return url;
	}
	
	/**
	 * Returns a set of zones for which this server is responsible.
	 * 
	 * @return
	 */
	public Set<String> getResponsibleZones() {
		return responsibleZones;
	}

}
