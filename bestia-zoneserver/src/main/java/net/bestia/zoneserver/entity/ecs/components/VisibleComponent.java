package net.bestia.zoneserver.entity.ecs.components;

import java.util.Objects;

import net.bestia.model.domain.SpriteInfo;
import net.bestia.zoneserver.entity.ecs.Component;

/**
 * The entity can be visualized by the engine. In order to do this some means of
 * information about the display visual art must be provided. Usually this is an
 * information about the sprite sheet to be used. Also different animations can
 * be used.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class VisibleComponent implements Component {
	
	private SpriteInfo spriteInfo;
	private boolean visible;

	/**
	 * Returns the sprite/visual representation of the entity.
	 * 
	 * @return The {@link SpriteInfo} of this entity.
	 */
	public SpriteInfo getVisual() {
		return spriteInfo;
	}

	/**
	 * Sets the visual representation of this entity.
	 * 
	 * @param visual
	 *            The new visual representation. Can not be null.
	 */
	public void setVisual(SpriteInfo visual) {
		this.spriteInfo = Objects.requireNonNull(visual);
	}

	/**
	 * Gives a flag if the entity is currently visible.
	 * 
	 * @return TRUE if the entity is visible. FALSE otherwise.
	 */
	public boolean isVisible() {
		return visible;
	}
}