package net.bestia.messages.internal;

import net.bestia.messages.Message;

/**
 * The {@link InternalMessage} descendants are not meant to be send to the
 * player. They therefore do not have an message id and are not entiteld to be
 * serialized by jackson. They are only used for inter-akka messaging. This
 * super class serves also as a message filter for the akka system. Generally
 * this message class is not needed since internal message could theoretically
 * inherit from {@link Message} but this would make it harder to route and
 * filter the messages inside the akka system which relies on instanceof checks.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * 
 */
public abstract class InternalMessage extends Message {

	private static final long serialVersionUID = 2015052401L;

	public InternalMessage() {
		// no op.
	}

	@Override
	public String toString() {
		return String.format("InternalMessage[]");
	}
}
