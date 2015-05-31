package net.bestia.zoneserver;


/**
 * Entrance to the bestia Zoneserver.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public final class App {
	
	public static void main(String[] args) {
		BestiaZoneserver zone = new BestiaZoneserver();
		zone.start();
	}

}
