package net.bestia.model.domain;

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
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Embeddable
public class SpriteInfo implements Serializable {

	private static final long serialVersionUID = 1L;

	@JsonProperty("s")
	public String sprite;

	@JsonProperty("t")
	@Enumerated(EnumType.STRING)
	public VisualType type;

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

	public VisualType getType() {
		return type;
	}

	public void setType(VisualType type) {
		this.type = type;
	}

	public String getSprite() {
		return sprite;
	}

	public void addSprite(String sprite) {
		this.sprite = sprite;
	}

	@Override
	public String toString() {
		return String.format("SpriteInfo[name: %s, type: %s]", sprite, type.toString());
	}
}
