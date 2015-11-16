package net.bestia.model.misc;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Sprite {

	public enum SpriteType {
		SINGLE, PACK, ITEM
	}

	/**
	 * Maby these classifications of the entity type should not be done in the
	 * visible component but rather in an own, dedicated component.
	 */
	public enum InteractionType {
		/**
		 * Simple mob on the map. The player can interact/attack it.
		 */
		MOB,

		/**
		 * NPCs who is primerly friendly.
		 */
		NPC,

		/**
		 * Items get a short "drop" animation when they appear and the player is
		 * able to click on them to interact via them.
		 */
		ITEM,

		/**
		 * The player can interact with the entity via clicking on it. The
		 * entity should handle such clicks via a script component.
		 */
		INTERACT,

		/**
		 * Generic entity. No special treatment in the engine. It will
		 * "just be displayed."
		 */
		GENERIC
	}

	@JsonProperty("n")
	private String name;
	
	@JsonProperty("a")
	private String animation;
	
	@JsonProperty("st")
	private SpriteType spriteType;
	
	@JsonProperty("it")
	private InteractionType interactionType;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getAnimation() {
		return animation;
	}

	public void setAnimation(String animation) {
		this.animation = animation;
	}

	public SpriteType getSpriteType() {
		return spriteType;
	}

	public void setSpriteType(SpriteType spriteType) {
		this.spriteType = spriteType;
	}

	public InteractionType getInteractionType() {
		return interactionType;
	}

	public void setInteractionType(InteractionType interactionType) {
		this.interactionType = interactionType;
	}
}
