package net.bestia.zoneserver.entity.traits;

import net.bestia.model.domain.Item;

/**
 * Entities having this trait can be loaded with a certain amount of items into
 * their local inventory and use this information to micromanage their own
 * inventory.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface Loadable extends Entity {

	/**
	 * Gives the maximum careable weight of this entity. It is pos. infinite if
	 * its unlimited.
	 * 
	 * @return The maximum item weight.
	 */
	float getMaxWeight();

	/**
	 * The current item weight.
	 * 
	 * @return Current item weight.
	 */
	float getWeight();

	/**
	 * The maximum number of items for this unit. Or -1 of its unlimited.
	 * 
	 * @return The maximum item count. -1 if unlimited.
	 */
	int getMaxItemCount();

	/**
	 * The current number of items stored in this entity.
	 * 
	 * @return Current number of items stored in this entity.
	 */
	int getItemCount();

	boolean addItem(Item item, int amount);

	boolean removeItem(Item item, int amount);

	boolean dropItem(Item item, int amount);
}
