package net.bestia.messages.internal;

/**
 * Incoming client messages are wrapped so that the root bestia actor can
 * identify them as client messages.
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
