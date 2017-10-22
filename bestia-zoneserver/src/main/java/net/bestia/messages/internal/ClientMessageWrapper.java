package net.bestia.messages.internal;

/**
 * Incoming client messages are wrapped for identification in the root bestia
 * actor.
 * 
 * @author Thomas Felix
 *
 */
public class ClientMessageWrapper {

	private final Object payload;

	public ClientMessageWrapper(Object msg) {

		this.payload = msg;
	}

	public Object getPayload() {
		return payload;
	}
}
