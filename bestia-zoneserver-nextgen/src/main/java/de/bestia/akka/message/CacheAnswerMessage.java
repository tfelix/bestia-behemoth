package de.bestia.akka.message;

public class CacheAnswerMessage {

	private final String key;
	private final Object value;
	private final InputMessage origMessage;

	public CacheAnswerMessage(String key, Object value) {
		this.key = key;
		this.value = value;
		this.origMessage = null;
	}

	public CacheAnswerMessage(CacheRequestMessage reqMsg, Object value, InputMessage origMessage) {
		this.key = reqMsg.getKey();
		this.value = value;
		this.origMessage = origMessage;
	}

	public String getKey() {
		return key;
	}

	public Object getValue() {
		return value;
	}
	
	public InputMessage getOrigMessage() {
		return origMessage;
	}
}
