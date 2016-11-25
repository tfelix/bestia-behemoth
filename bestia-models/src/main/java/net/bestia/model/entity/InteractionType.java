package net.bestia.model.entity;

public enum InteractionType {

	/**
	 * Simple mob on the map. The player can interact/attack it.
	 */
	ATTACKABLE,

	/**
	 * NPCs who is usually friendly.
	 */
	TALKABLE,

	/**
	 * Items get a short "drop" animation when they appear and the player is
	 * able to click on them to interact via them.
	 */
	PICKABLE,

	/**
	 * The player can interact with the entity via clicking on it. The entity
	 * should handle such clicks via a script component.
	 */
	INTERACT,

	/**
	 * Generic entity. No special treatment in the engine. It will
	 * "just be displayed."
	 */
	NONE

}
