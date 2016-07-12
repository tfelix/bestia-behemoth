package de.bestia.akka.message;

public class InputMessage {
	
	private final long playerBestiaId;
	private final String payload;
	
	public InputMessage(long id, String payload) {
		
		this.playerBestiaId = id;
		this.payload = payload;
	}
	
	public long getPlayerBestiaId() {
		return playerBestiaId;
	}
	
	public String getPayload() {
		return payload;
	}

}
