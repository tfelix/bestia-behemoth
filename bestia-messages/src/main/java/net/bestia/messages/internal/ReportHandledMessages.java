package net.bestia.messages.internal;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import net.bestia.messages.Message;

/**
 * The message is used to report back to paretn actors which message IDs are
 * handled by the actor and its child-actors. The routing can then create the
 * message routing table.
 * 
 * @author Thomas Felix
 *
 */
public class ReportHandledMessages {

	private Set<Class<? extends Object>> handledMessages = new HashSet<>();

	/**
	 * Empty message means it handles no messages.
	 */
	public ReportHandledMessages() {
		// no op.
	}

	/**
	 * 
	 * @param handledMessages
	 */
	public ReportHandledMessages(Set<Class<? extends Object>> handledMessages) {
		Objects.requireNonNull(handledMessages);
		this.handledMessages.addAll(handledMessages);
	}

	/**
	 * Helpful ctor to add new handled classes to an already existing
	 * {@link ReportHandledMessages} object.
	 * 
	 * @param parent
	 *            A already existing message handling report.
	 * @param handledMessages
	 *            The list of classes are joined with these of parent.
	 */
	public ReportHandledMessages(ReportHandledMessages parent, Set<Class<? extends Message>> handledMessages) {
		Objects.requireNonNull(handledMessages);

		this.handledMessages.addAll(handledMessages);
		this.handledMessages.addAll(parent.getHandledMessages());
	}

	/**
	 * Returns the collection of message type classes which are handled by the
	 * actor.
	 * 
	 * @return A unmodifiable collection of message class types.
	 */
	public Collection<Class<? extends Object>> getHandledMessages() {
		return Collections.unmodifiableCollection(handledMessages);
	}

	@Override
	public String toString() {
		return String.format("ReportHandledMessages[handles: %s]", handledMessages.toString());
	}
}
