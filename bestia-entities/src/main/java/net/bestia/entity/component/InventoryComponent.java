package net.bestia.entity.component;

import net.bestia.model.domain.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Entities having this trait can be loaded with a certain amount of items into
 * their local inventory and use this information to micro manage their own
 * inventory.
 *
 * @author Thomas Felix
 */
@ComponentSync(SyncType.OWNER)
public class InventoryComponent extends Component {

	/**
	 * Constant for meaning that the item count is basically not limited.
	 */
	public static final int UNLIMITED_ITEMS = -1;
	private static final long serialVersionUID = 1L;

	private float maxWeight;
	private int maxItemCount;

	private List<ItemCount> items = new ArrayList<>();

	public InventoryComponent(long id) {
		super(id);

		maxWeight = 0;
		maxItemCount = UNLIMITED_ITEMS;
	}

	/**
	 * Gives the maximum carryable weight of this entity. It is pos. infinite if
	 * its unlimited.
	 *
	 * @return The maximum item weight.
	 */
	public float getMaxWeight() {
		return maxWeight;
	}

	/**
	 * Sets the maximum weight this entity can carry.
	 *
	 * @param maxWeight The maximum weight in units. One unit is 0.1kg.
	 */
	void setMaxWeight(int maxWeight) {
		if (maxWeight < 0) {
			throw new IllegalArgumentException("MaxWeight can not be negative.");
		}

		this.maxWeight = maxWeight;
	}

	/**
	 * The current item weight in kg. The item weight per item is saved as 0.1kg
	 * per unit.
	 *
	 * @return Current item weight.
	 */
	public float getWeight() {
		return items.stream()
				.mapToInt(x -> x.amount * x.item.getWeight())
				.sum() / 10f;
	}

	/**
	 * The maximum number of items for this unit. Or -1 of its unlimited.
	 *
	 * @return The maximum item count. -1 if unlimited.
	 */
	public int getMaxItemCount() {
		return maxItemCount;
	}

	public void setMaxItemCount(int maxItemCount) {
		this.maxItemCount = maxItemCount;
	}

	/**
	 * The current number of item (slots) stored in this entity.
	 *
	 * @return Current number of item slots stored in this entity.
	 */
	public int getItemCount() {
		return items.size();
	}

	/**
	 * Adds the new item to the inventory. But this will work only if the number
	 * of item slots are not exceeded and the total amount of weight does not be
	 * bigger as the maximum amount of weight.
	 *
	 * @param item   The item to be added.
	 * @param amount The amount of the item to be added to the inventory.
	 * @return TRUE if the item has been added to the inventory. FALSE
	 * otherwise.
	 */
	public boolean addItem(Item item, int amount) {
		// Check count.
		if (getItemCount() + 1 > getMaxItemCount()) {
			return false;
		}

		// Check weight.
		if (item.getWeight() / .1f + getWeight() > getMaxWeight()) {
			return false;
		}

		items.add(new ItemCount(item, amount));
		return true;
	}

	/**
	 * Removes the item from the inventory. It returns true only if the item
	 * could be successfully be removed.
	 *
	 * @param item
	 * @param amount
	 * @return
	 */
	public boolean removeItem(Item item, int amount) {
		Optional<ItemCount> inventoryItem = items.stream()
				.filter(x -> x.item.getId() == item.getId())
				.findFirst();
		if (inventoryItem.isPresent()) {

			ItemCount ic = inventoryItem.get();

			if (ic.amount > amount) {
				ic.amount -= amount;
				return true;
			} else if (ic.amount == amount) {
				items.remove(ic);
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(items, maxItemCount, maxWeight);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (!(obj instanceof InventoryComponent)) {
			return false;
		}
		final InventoryComponent other = (InventoryComponent) obj;
		return Objects.equals(items, other.items)
				&& Objects.equals(maxItemCount, other.maxItemCount)
				&& Objects.equals(maxWeight, other.maxWeight);
	}

	@Override
	public String toString() {
		return String.format("InventoryComp[maxWeight: %.1f, items: %d/%d]", getMaxWeight(), items.size(),
				getMaxItemCount());
	}

	private static class ItemCount {
		public int amount;
		public Item item;

		public ItemCount(Item item, int amount) {

			this.amount = amount;
			this.item = item;
		}
	}
}
