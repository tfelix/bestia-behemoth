package net.bestia.zoneserver.entity.traits;

import net.bestia.model.domain.SpriteInfo;

/**
 * The entity can be visualized by the engine. In order to do this some means of
 * information about the display visual art must be provided. Usually this is an
 * information about the sprite sheet to be used. Also different animations can
 * be used.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface Visible extends Locatable {

	/**
	 * Returns the sprite/visual representation of the entity.
	 * 
	 * @return The {@link SpriteInfo} of this entity.
	 */
	SpriteInfo getVisual();

	/**
	 * Sets the visual representation of this entity.
	 * 
	 * @param visual
	 *            The new visual representation. Can not be null.
	 */
	void setVisual(SpriteInfo visual);

	/**
	 * Gives a flag if the entity is currently visible.
	 * 
	 * @return TRUE if the entity is visible. FALSE otherwise.
	 */
	boolean isVisible();

	// Filter to alter the visual representation of this unit.
	/*
	 * void addVisualFilter();
	 * 
	 * void removeVisualFilter();
	 */
}
