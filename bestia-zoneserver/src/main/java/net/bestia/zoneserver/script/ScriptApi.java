package net.bestia.zoneserver.script;

import java.util.List;

import net.bestia.messages.chat.ChatMessage;
import net.bestia.model.geometry.CollisionShape;

/**
 * Global script API used by all scripts in the bestia system to interact with
 * the behemoth server.
 * 
 * @author Thomas Felix
 *
 */
public interface ScriptApi {

	/**
	 * Logs the info text into the script logger.
	 * 
	 * @param text
	 *            The text to log.
	 */
	void info(String text);

	/**
	 * Logs the debug text into the script logger.
	 * 
	 * @param text
	 *            The text to log.
	 */
	void debug(String text);

	// ############# ENTITY API #############
	long createEntity(CollisionShape shape);

	List<Long> findEntities(long x, long y, long width, long height);

	/**
	 * Checks if the entity is a given type. Returns TRUE if the entity belongs
	 * to this type or FALSE otherwise.
	 * 
	 * Valid types (string) are:
	 * <ul>
	 * <li>PLAYER</li>
	 * <li>SCRIPT</li>
	 * </ul>
	 * 
	 * @param entityId
	 * @param type
	 * @return
	 */
	boolean isEntityTypeOf(long entityId, String type);

	void setLivetime(long entityId, int livetimeMs);

	/**
	 * Kills the entity with the given entity id. It behaves as if it would have
	 * been regularly killed by a player and thus plays an death animation.
	 * 
	 * @param entityId
	 *            The entity ID to be killed.
	 */
	void kill(long entityId);

	/**
	 * The given entity ID is just deleted and immediately removed from the
	 * system. No death animation is played back to the client.
	 * 
	 * @param entityId
	 *            The entity ID to be removed.
	 */
	void delete(long entityId);

	/**
	 * Defines a callback function which gets attached to the script and is
	 * periodically called.
	 * 
	 * @param entityId
	 * @param scriptName
	 * @param callbackName
	 */
	void setInterval(long entityId, String scriptName, String callbackName, int delayMs);

	void setOnEnter(long entityId, String callbackName);

	void setOnLeave(long entityId, String callbackName);

	void setVisual(long entityId, String spriteName);

	void playAnimation(long entityId, String animationName);

	void setPosition(long entityId, long x, long y);

	void setShape(long entityId, CollisionShape shape);

	void sendMessage(long playerEntityId, String message, String mode);
}
