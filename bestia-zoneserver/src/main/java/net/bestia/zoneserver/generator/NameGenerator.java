package net.bestia.zoneserver.generator;

/**
 * This generator provides the game with new names for various things in the
 * game.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface NameGenerator {

	/**
	 * Generate a random name.
	 * 
	 * @return The new random name.
	 */
	String generateName();

}