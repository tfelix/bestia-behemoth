package net.bestia.messages;


/**
 * This message contains a list of (visible) entities around a player. These
 * entities consist out of a sprite, a position and additional data that should
 * be preloaded and is associated with an entity. For example existing sounds or
 * attack animations which can be triggered spontaneously. These should be
 * loaded as soon as possible from the client engine.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapEntitiesMessage extends Message {

	private static final long serialVersionUID = 1L;
	public final static String MESSAGE_ID = "map.entites";

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getClientMessagePath();
	}

}
