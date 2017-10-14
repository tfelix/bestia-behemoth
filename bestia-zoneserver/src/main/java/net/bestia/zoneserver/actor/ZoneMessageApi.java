package net.bestia.zoneserver.actor;

import akka.actor.ActorRef;
import net.bestia.messages.MessageApi;

/**
 * Extention of the normal {@link MessageApi} interface. We need this hack to
 * include a method which must be called by our local implementation of the
 * local actor. The external interface only can not be used because this will
 * proxy away our method.
 * 
 * @author Thomas Felix
 *
 */
public interface ZoneMessageApi extends MessageApi {

	/**
	 * Sets the actor ref to be used by the api.
	 * 
	 * @param msgRouter
	 */
	public void setMessageEntry(ActorRef msgRouter);
}
