package net.bestia.model.entity;

/**
 * Interaction types give the client hints how the player is able to interact
 * with an entity. Based upon the available options it should present the player
 * with appropriate UI options.
 * 
 * @author Thomas Felix
 *
 */
public enum InteractionType {

	/**
	 * The player can possibly attack this entity.
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
	 * Generic entity. No special treatment in the engine. It will "just be
	 * displayed."
	 */
	NONE

}
