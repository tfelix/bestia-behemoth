package net.bestia.zoneserver.entity.traits;

import net.bestia.model.entity.Sprite;

/**
 * The entity can be visualized by the engine. In order to do this some means of
 * information about the display visual art must be provided. Usually this is an
 * information about the sprite sheet to be used. Also different animations can
 * be used.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface Visible {

	/**
	 * Returns the sprite of the entity.
	 * 
	 * @return
	 */
	Sprite getSprite();

	/**
	 * Gives a flag if the entity is visible.
	 * 
	 * @return
	 */
	boolean isVisible();

}
