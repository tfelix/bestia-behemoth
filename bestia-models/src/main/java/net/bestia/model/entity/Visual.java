package net.bestia.model.entity;

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
public class Visual implements Serializable {

	private static final long serialVersionUID = 1L;

	@JsonProperty("n")
	private String name;

	@JsonProperty("st")
	private VisualType visualType;

	public Visual() {
		// no op.
	}

	public Visual(String name, VisualType type) {
		
		this.name = Objects.requireNonNull(name);
		this.visualType = type;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public VisualType getSpriteType() {
		return visualType;
	}

	public void setVisualType(VisualType spriteType) {
		this.visualType = spriteType;
	}
	
	@Override
	public String toString() {
		return String.format("Visual[name: %s, type: %s]", name, visualType.toString());
	}
}
