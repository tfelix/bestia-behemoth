package net.entity.component;

import net.bestia.entity.component.Component;
import net.bestia.entity.component.ComponentSync;
import net.bestia.entity.component.SyncType;

import java.util.Objects;

/**
 * This component describes which item is contained inside this entity with
 * which amount.
 * 
 * @author Thomas Felix
 *
 */
@ComponentSync(SyncType.ALL)
public class ItemComponent extends Component {

	private static final long serialVersionUID = 1L;
	
	private int amount;
	private long itemId;

	public ItemComponent(long id) {
		super(id);

	}

	public void setAmount(int amount) {
		this.amount = amount;
	}
	
	public void setItemId(long itemId) {
		this.itemId = itemId;
	}
	
	public int getAmount() {
		return amount;
	}
	
	public long getItemId() {
		return itemId;
	}

	@Override
	public int hashCode() {
		return Objects.hash(amount, itemId);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (!(obj instanceof ItemComponent)) {
			return false;
		}
		ItemComponent other = (ItemComponent) obj;
		if (amount != other.amount) {
			return false;
		}
		if (itemId != other.itemId) {
			return false;
		}
		return true;
	}
}
