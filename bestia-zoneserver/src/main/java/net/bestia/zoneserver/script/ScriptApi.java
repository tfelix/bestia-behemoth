package net.bestia.zoneserver.script;

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

	long createEntity(CollisionShape shape);

	void setLivetime(long entityId, int livetimeMs);

	/**
	 * Kills the entity with the given entity id. It behaves as if it would have
	 * been regularly killed by a player.
	 * 
	 * @param entityId
	 *            The entity ID to be killed.
	 */
	void kill(long entityId);

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

}