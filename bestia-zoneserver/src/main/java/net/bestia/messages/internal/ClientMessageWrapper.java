package net.bestia.messages.internal;

public class ClientMessageWrapper {
	
	private final Object payload;

	public ClientMessageWrapper(Object msg) {
		
		this.payload = msg;
	}

	public Object getPayload() {
		return payload;
	}
}
