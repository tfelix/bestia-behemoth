package net.bestia.messages.internal;

import net.bestia.messages.Message;

/**
 * The {@link SystemMessage} descendants are not meant to be send to the player.
 * They therefore do not have an message id and are not entiteld to be
 * serialized by jackson. They are only used for inter-akka messaging.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * 
 */
public abstract class SystemMessage extends Message {

	private static final long serialVersionUID = 2015052401L;

	public SystemMessage() {
		// no op.
	}

	@Override
	public String toString() {
		return String.format("SystemMessage[]");
	}
}
