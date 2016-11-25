package net.bestia.zoneserver.entity.traits;

import java.util.Set;

import net.bestia.model.entity.InteractionType;

/**
 * Entities which implement this interface have some kind of intractable
 * behaviour. This usually can be different types of interactions. A NPC might
 * be able to talk while other entities might be able to get clicked.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface Interactable {

	/**
	 * Asks the entity for all types of interactions which are possible with it
	 * by the certain entity.
	 * 
	 * @return
	 */
	Set<InteractionType> getInteractions();
}
