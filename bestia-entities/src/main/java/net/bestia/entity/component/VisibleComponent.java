package net.bestia.entity.component;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

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
@ComponentSync(SyncType.ALL)
public class VisibleComponent extends Component {

	private static final long serialVersionUID = 1L;
	private SpriteInfo spriteInfo;
	private boolean visible;

	public VisibleComponent(long id) {
		super(id);
		clear();
	}

	@Override
	public void clear() {
		spriteInfo = SpriteInfo.empty();
		visible = true;
	}

	/**
	 * Returns the sprite/visual representation of the entity.
	 * 
	 * @return The {@link SpriteInfo} of this entity.
	 */
	@JsonProperty("v")
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
	 * @TODO This should ne be synced to the client then to prevent cheating.
	 * Better way would be to change this component into another component
	 * with other sync settings and then reset to this component.
	 * 
	 * @return TRUE if the entity is visible. FALSE otherwise.
	 */
	@JsonProperty("vis")
	public boolean isVisible() {
		return visible;
	}

	@Override
	public int hashCode() {
		return Objects.hash(spriteInfo, visible);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (!(obj instanceof VisibleComponent)) {
			return false;
		}
		final VisibleComponent other = (VisibleComponent) obj;
		return Objects.equals(spriteInfo, other.spriteInfo)
				&& Objects.equals(visible, other.visible);
	}

	@Override
	public String toString() {
		return String.format("VisibleComponent[%s, visible: %b]", spriteInfo.toString(), visible);
	}
}
