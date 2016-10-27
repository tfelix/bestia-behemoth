package net.bestia.messages;

/**
 * Messages can be identified by returning a unique id.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface MessageId {

	/**
	 * Returns the id of this message. The same id is used on the client to
	 * trigger events which have subscribed for the arrival of this kind of
	 * messages.
	 * 
	 * @return Event name to be triggered on the client.
	 */
	String getMessageId();
}