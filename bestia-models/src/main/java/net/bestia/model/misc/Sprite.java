package net.bestia.model.misc;

import java.io.Serializable;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Holds basic informations about the display of a sprite. The class will not be
 * saved into the database but it must be send to the client from the server.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Sprite implements Serializable {

	private static final long serialVersionUID = 1L;

	@JsonProperty("n")
	private String name;

	@JsonProperty("st")
	private SpriteType spriteType;

	public Sprite() {
		// no op.
	}

	public Sprite(String name, SpriteType type) {
		
		this.name = Objects.requireNonNull(name);
		this.spriteType = type;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public SpriteType getSpriteType() {
		return spriteType;
	}

	public void setSpriteType(SpriteType spriteType) {
		this.spriteType = spriteType;
	}
}
