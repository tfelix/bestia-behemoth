package net.bestia.webserver.bestia;

import java.io.IOException;
import java.util.UUID;

import net.bestia.core.BestiaZoneserver;
import net.bestia.core.connection.BestiaConnectionInterface;
import net.bestia.core.message.Message;

public class BestiaNettosphereConnection implements BestiaConnectionInterface {
	
	private static BestiaNettosphereConnection INSTANCE;
	private BestiaWebsocket nettoSphere;
	private BestiaZoneserver zone;
	
	private BestiaNettosphereConnection() {
		
	}
	
	public synchronized static BestiaNettosphereConnection getInstance() {
		if(INSTANCE == null) {
			INSTANCE = new BestiaNettosphereConnection();
		}
		return INSTANCE;
	}
	
	public void setNettosphere(BestiaWebsocket nettoSphere) {
		this.nettoSphere = nettoSphere;
	}
	
	public void setZone(BestiaZoneserver zone) {
		this.zone = zone;
	}
	
	public BestiaZoneserver getZone() {
		return zone;
	}

	@Override
	public void sendMessage(Message message) throws IOException {
		nettoSphere.sendMessage(message);
	}

	@Override
	public boolean isConnected(int accountId) {
		return nettoSphere.isConnected(accountId);
	}


	@Override
	public void dropConnection(UUID connectionId) {
		nettoSphere.dropConnection(connectionId);
	}

}
