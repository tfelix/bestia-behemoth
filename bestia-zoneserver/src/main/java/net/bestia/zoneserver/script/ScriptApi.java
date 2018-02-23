package net.bestia.zoneserver.script;

import net.bestia.model.geometry.CollisionShape;

import java.util.List;

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

	/**
	 * Spawns a mob entity with the given database name.
	 * 
	 * @param mobDbName
	 *            The mob database name.
	 * @param x
	 *            X position of the mob.
	 * @param y
	 *            Y position of the mob.
	 * @return The entity id of this mob entity.
	 */
	long spawnMob(String mobDbName, long x, long y);

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

	/**
	 * Kills the entity with the given entity id. It behaves as if it would have
	 * been regularly killed by a player and thus plays an death animation.
	 * 
	 * @param entityId
	 *            The entity ID to be killed.
	 */
	void kill(long entityId);

	/**
	 * Checks if the given entity ID exists.
	 * 
	 * @param entityId
	 * @return
	 */
	boolean exists(long entityId);

	/**
	 * The given entity ID is just deleted and immediately removed from the
	 * system. No death animation is played back to the client.
	 * 
	 * @param entityId
	 *            The entity ID to be removed.
	 */
	void delete(long entityId);

	void setScriptVar(String scriptKey, String data);

	String getScriptVar(String scriptKey);

	/**
	 * Defines a callback function which gets attached to the script and is
	 * periodically called.
	 * 
	 * @param entityId
	 * @param scriptName
	 */
	void setInterval(long entityId, String scriptName, int delayMs);

	void setOnEnter(long entityId, String callbackName);

	void setOnLeave(long entityId, String callbackName);

	void setVisual(long entityId, String spriteName);

	void playAnimation(long entityId, String animationName);

	void setPosition(long entityId, long x, long y);

	void setShape(long entityId, CollisionShape shape);

	/**
	 * Sends a chat message to the user/owner of this given player entity id
	 * entity.
	 * 
	 * @param playerEntityId
	 *            The entity which should be a player entity.
	 * @param message
	 *            The message to be send.
	 * @param mode
	 *            The chat mode under which the message should arrive. Default
	 *            is "SYSTEM".
	 */
	void sendMessage(long playerEntityId, String message, String mode);
}
