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
	
	public ServerInfoMessage(Message msg) {
		super(msg);
	}
	
	public ServerInfoMessage() {
		
	}

	public ServerInfoMessage(Message msg, List<String> zones, int connectedPlayer, String resUrl) {
		super(msg);
		this.zones = zones;
		this.connectedPlayer = connectedPlayer;
		this.ressourceURL = resUrl;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String toString() {
		return String.format(
				"ServerInfoMessage[zones: %s, players: %d, ressourceURL: %s]",
				zones.toString(), connectedPlayer, ressourceURL);
	}

}
