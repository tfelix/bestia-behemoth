package net.bestia.zoneserver.entity;

import net.bestia.messages.Message;

public interface EntityContext {

	/**
	 * Sends a message to the client or clients. The messaging framework will
	 * check to find out to whom the message must be delivered.
	 * 
	 * @param msg
	 *            The message which shall be send to the client(s).
	 */
	void sendMessage(Message msg);

	/**
	 * Signals if a new entity was spawned from inside the system (maybe a
	 * script source etc.). Usually the akka system need to react upon this
	 * event and setup some actors.
	 * 
	 * @param entityId
	 *            The entity ID of the freshly spawned entity.
	 */
	void entitySpawned(long entityId);
}