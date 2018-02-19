package bestia.model.domain;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Embeddable;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This class is meant to be included in different messages. The engine must be
 * informed when getting the player bestia data how to display it and also when
 * an entity gets an update.
 * 
 * @author Thomas Felix
 *
 */
@Embeddable
public class SpriteInfo implements Serializable {

	private static final long serialVersionUID = 1L;

	private static final SpriteInfo EMPTY_SPRITE;

	static {
		EMPTY_SPRITE = new SpriteInfo();
		EMPTY_SPRITE.setType(VisualType.SINGLE);
		EMPTY_SPRITE.setSprite("");
	}

	@JsonProperty("s")
	public String sprite;

	@JsonProperty("t")
	@Enumerated(EnumType.STRING)
	public VisualType type = VisualType.PACK;

	/**
	 * For Jackson Only.
	 */
	SpriteInfo() {
		// no op.
	}

	/**
	 * Ctor.
	 * 
	 * @param sprite
	 *            The name of the sprite.
	 * @param type
	 *            The type of the sprite.
	 */
	public SpriteInfo(String sprite, VisualType type) {

		this.sprite = Objects.requireNonNull(sprite);
		this.type = type;
	}

	private SpriteInfo(SpriteInfo rhs) {
		this(rhs.getSprite(), rhs.getType());
	}

	/**
	 * Constructs an empty placeholder visual which can be seen as such inside
	 * the engine.
	 * 
	 * @return A invisible placeholder sprite info.
	 */
	public static SpriteInfo empty() {
		return new SpriteInfo(EMPTY_SPRITE);
	}

	/**
	 * Creates an sprite info for a item sprite which is usually only a static
	 * image.
	 * 
	 * @param image
	 *            Name of the static item sprite image.
	 * @return A {@link SpriteInfo} instance describing the item.
	 */
	public static SpriteInfo item(String image) {
		return new SpriteInfo(image, VisualType.ITEM);
	}

	public VisualType getType() {
		return type;
	}

	public void setType(VisualType type) {
		this.type = type;
	}

	public String getSprite() {
		return sprite;
	}

	public void setSprite(String sprite) {
		this.sprite = sprite;
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, sprite);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof SpriteInfo)) {
			return false;
		}
		final SpriteInfo other = (SpriteInfo) obj;
		return Objects.equals(type, other.type)
				&& Objects.equals(sprite, other.sprite);
	}

	@Override
	public String toString() {
		return String.format("SpriteInfo[name: %s, type: %s]", sprite, type.toString());
	}
}
