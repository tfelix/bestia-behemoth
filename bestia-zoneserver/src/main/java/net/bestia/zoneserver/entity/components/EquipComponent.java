package net.bestia.zoneserver.entity.components;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import net.bestia.model.domain.EquipmentSlot;
import net.bestia.model.domain.Item;

/**
 * Entities implementing this interface can be equipped with items.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class EquipComponent extends Component {

	private static final long serialVersionUID = 1L;
	
	private final Set<EquipmentSlot> slots = new HashSet<>();
	private final Set<Item> equipments = new HashSet<>();

	public EquipComponent(long id) {
		super(id);
		// no op.
	}

	/**
	 * Equips the item and apply any status effects bundled with this item. Only
	 * equipment type items can be equipped.
	 * 
	 * @param item
	 *            The item to equip.
	 */
	public void equip(Item item) {
		equipments.add(item);
	}

	/**
	 * Removes the item and removes all status effects associated with it.
	 * 
	 * @param item
	 *            The item to remove.
	 */
	public void unequip(Item item) {
		equipments.remove(item);
	}

	/**
	 * Returns the set of equipment slots which are available for equipping
	 * items. Some items might have restrictions for equipping them into these
	 * slots whatsoever.
	 * 
	 * @return A set of available equipment slots.
	 */
	public Set<EquipmentSlot> getAvailableEquipmentSlots() {
		return Collections.unmodifiableSet(slots);
	}
	
	public void getAvailableEquipmentSlots(Set<EquipmentSlot> slots) {
		Objects.requireNonNull(slots);
		this.slots.clear();
		this.slots.addAll(slots);
	}
	
	@Override
	public String toString() {
		return String.format("EquipComponent[id: %d]", getId());
	}
}
