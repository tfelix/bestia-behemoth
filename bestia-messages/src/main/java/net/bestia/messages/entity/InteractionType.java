package net.bestia.messages.entity;

/**
 * Determines how to interact with a certain entity. Note that for some entities
 * this default behaviour can be overridden: forcing the attack against an NPC
 * for example.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public enum InteractionType {

	/**
	 * No interaction is possible.
	 */
	NONE,
	
	/**
	 * Entity can be attacked.
	 */
	ATTACK,
	
	/**
	 * With the entity can be interacted.
	 */
	INTERACT
	
}
