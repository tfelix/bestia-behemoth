package net.bestia.messages.system;

import java.util.Collection;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.AccountMessage;

/**
 * Returns basic information about this zoneserver for debugging purposes.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public final class ServerInfoMessage extends AccountMessage {

	private static final long serialVersionUID = 1L;

	public final static String MESSAGE_ID = "server.info";

	@JsonProperty("z")
	private Collection<String> zones;

	@JsonProperty("v")
	private String version = "VERSIONEINFÜGEN";

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
	
	public ServerInfoMessage(AccountMessage msg) {
		super(msg);
	}
	
	public ServerInfoMessage(AccountMessage msg, Collection<String> zones, String zoneName, int connectedPlayer, String resUrl) {
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

	@Override
	public String getMessagePath() {
		return getClientMessagePath(getAccountId());
	}
}
