package de.bestia.akka.message;

public class CacheRequestMessage {

	private final String key;
	private final InputMessage originalMessage;
	
	public CacheRequestMessage(String key, InputMessage originalMessage) {
		this.key = key;
		this.originalMessage = originalMessage;
	}
	
	public String getKey() {
		return key;
	}
	
	public InputMessage getOriginalMessage() {
		return originalMessage;
	}
}
