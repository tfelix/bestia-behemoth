package net.bestia.entity.component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import net.bestia.model.domain.EquipmentSlot;
import net.bestia.model.domain.Item;

/**
 * Entities owning this component are able to equip items.
 * 
 * @author Thomas Felix
 *
 */
@ComponentSync(SyncType.OWNER)
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
	public int hashCode() {
		return Objects.hash(slots, equipments);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (!(obj instanceof EquipComponent)) {
			return false;
		}
		final EquipComponent other = (EquipComponent) obj;
		return Objects.equals(equipments, other.equipments)
				&& Objects.equals(slots, other.slots);
	}

	@Override
	public String toString() {
		return String.format("EquipComponent[id: %d]", getId());
	}
}
