package net.bestia.messages;

import java.io.Serializable;

/**
 * Base for all messages in the bestia server.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * 
 */

public abstract class Message implements Serializable {

	private static final long serialVersionUID = 2015052401L;

	public Message() {
		// no op.
	}

	@Override
	public String toString() {
		return "Message[]";
	}
}
