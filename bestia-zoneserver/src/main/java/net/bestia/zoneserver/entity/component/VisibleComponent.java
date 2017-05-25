package net.bestia.zoneserver.entity.component;

import java.util.Objects;

import net.bestia.model.domain.SpriteInfo;

/**
 * The entity can be visualized by the engine. In order to do this some means of
 * information about the display visual art must be provided. Usually this is an
 * information about the sprite sheet to be used. Also different animations can
 * be used.
 * 
 * @author Thomas Felix
 *
 */
public class VisibleComponent extends Component {

	private static final long serialVersionUID = 1L;
	private SpriteInfo spriteInfo = SpriteInfo.empty();
	private boolean visible = true;
	
	public VisibleComponent(long id, long entityId) {
		super(id, entityId);
		// no op.
	}

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
	
	@Override
	public String toString() {
		return String.format("VisibleComponent[%s, visible: %b]", spriteInfo.toString(), visible);
	}
}
