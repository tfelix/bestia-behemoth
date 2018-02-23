package net.bestia.zoneserver.equip;

import org.springframework.stereotype.Service;

import net.entity.Entity;
import bestia.model.domain.Item;

@Service
public class EquipService {

	
	/**
	 * Checks if all prerequisites are fulfilled in order to equip this item to
	 * the entity. If another item already occupied this slot the method wont
	 * return false. Upon equipping the other item will rather get removed from
	 * this equipment slot.
	 * 
	 * @param item
	 *            The item to check if it can be equipped.
	 * @return TRUE if the item can currently be equipped. FALSE otherwise.
	 */
	public boolean canEquip(Entity entity, Item item) {
		
		return false;
	}
}
