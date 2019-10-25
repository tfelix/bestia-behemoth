package de.tfelix.bestia.ai;

import java.io.Serializable;

/**
 * This is a arbitrary representation of a world place. The AI dont need to know
 * how a point in space is exactly made up. The AI engine only needs certain
 * information from a point like the distance to another point. These
 * information must be provided by the game engine itself.
 * 
 * @author Thomas Felix
 *
 */
public interface AIPoint extends Serializable {
	// no op.
}
