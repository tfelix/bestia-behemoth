package net.bestia.messages.internal;

import net.bestia.messages.Message;

/**
 * This message is used to send a script interval request to the associated
 * actor.
 * 
 * @author Thomas Felix
 *
 */
public class ScriptIntervalMessage extends Message {

	private static final long serialVersionUID = 1L;

	private final long scriptEntityId;
	private final int delay;

	public ScriptIntervalMessage(long scriptEntityId, int delay) {

		this.scriptEntityId = scriptEntityId;
		this.delay = delay;
	}

	public long getScriptEntityId() {
		return scriptEntityId;
	}

	public int getDelay() {
		return delay;
	}

	@Override
	public String toString() {
		return String.format("ScriptIntervalMessage[scriptEntity: %d, delay: %d]", getScriptEntityId(), getDelay());
	}
}
