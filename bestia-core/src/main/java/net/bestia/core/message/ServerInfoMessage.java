package net.bestia.core.message;

import java.util.List;

import net.bestia.core.BestiaZoneserver;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Returns basic information about this zoneserver for debugging purposes.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public final class ServerInfoMessage extends Message {

	public final static String MESSAGE_ID = "server.info";

	@JsonProperty("z")
	private List<String> zones;

	@JsonProperty("v")
	private String version = BestiaZoneserver.VERSION;

	@JsonProperty("cp")
	private int connectedPlayer;

	@JsonProperty("res")
	private String ressourceURL;

	@JsonProperty("zn")
	private String zoneName;
	
	@JsonProperty("st")
	private long serverTime;
	
	public ServerInfoMessage() {
		// no op.
	}

	public ServerInfoMessage(Message msg) {
		super(msg);
	}

	public ServerInfoMessage(Message msg, List<String> zones, String zoneName, int connectedPlayer, String resUrl) {
		super(msg);
		this.zones = zones;
		this.connectedPlayer = connectedPlayer;
		this.ressourceURL = resUrl;
		this.zoneName = zoneName;
		this.serverTime = System.currentTimeMillis();
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String toString() {
		return String.format("ServerInfoMessage[zoneName: %s, zones: %s, players: %d, ressourceURL: %s, version: %s, serverTime: %d]",
				zoneName, zones.toString(), connectedPlayer, ressourceURL, version, serverTime);
	}

}
