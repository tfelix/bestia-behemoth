package net.bestia.model.item;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import net.bestia.model.account.Account;
import net.bestia.model.item.Item;
import net.bestia.model.item.ItemType;

@Entity
@Table(name = "player_items", uniqueConstraints = { @UniqueConstraint(columnNames = {
		"ITEM_ID", "ACCOUNT_ID" }) })
public class PlayerItem implements Serializable {

	@Transient
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(unique = true, nullable = false)
	@JsonProperty("pid")
	private int id;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "ITEM_ID", nullable = false)
	@JsonProperty("i")
	private Item item;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ACCOUNT_ID", nullable = false)
	@JsonIgnore
	private Account account;

	/**
	 * Std. ctor for Hibernate.
	 */
	public PlayerItem() {
		// no op.
	}

	/**
	 * Ctor.
	 * 
	 * @param item
	 *            Item to add to the player inventory.
	 * @param account
	 *            The player account to add the item.
	 * @param amount
	 *            The amount of the item to be added.
	 */
	public PlayerItem(Item item, Account account, int amount) {
		setItem(item);
		setAccount(account);
		setAmount(amount);
	}

	/**
	 * Number of items inside the players inventory.
	 */
	@JsonProperty("a")
	private int amount;

	public Item getItem() {
		return item;
	}

	/**
	 * Sets the item. Since there is some special treatment to equipment type
	 * items the amount of equipment can only be 1 (equipment can not be stacked
	 * inside the inventory, since additional information is attached to each
	 * player item equipment).
	 * 
	 * @param item
	 */
	public void setItem(Item item) {

		if (item == null) {
			throw new IllegalArgumentException("Item can not be null.");
		}

		if (item.getType() == ItemType.EQUIP) {
			throw new IllegalArgumentException(
					"Items which are equipment can not be set without and "
							+ "equipment item info object. Please use setItem(Item, "
							+ "EquipItemInfo) instead.");
		}

		this.item = item;
	}

	public int getId() {
		return id;
	}

	/**
	 * The account to whom the item belongs.
	 * 
	 * @return Account who owns the item.
	 */
	public Account getAccount() {
		return account;
	}

	/**
	 * Sets the owner of the item. Note that there can not be one account owning
	 * the same number of items. Just add the amount instead. Setting the
	 * account to null wont work. Delete the relationship instead.
	 * 
	 * @param account
	 */
	public void setAccount(Account account) {
		if (account == null) {
			throw new IllegalArgumentException("Account can not be null.");
		}
		this.account = account;
	}

	/**
	 * The amount of the item in the inventory.
	 * 
	 * @return The amount of the item.
	 */
	public int getAmount() {
		return amount;
	}

	/**
	 * Sets the amount of the owned item. Amount must be bigger then 0.
	 * Otherwise delete the relationship. Amounts of equipment type items can
	 * not be different then 1.
	 * 
	 * @param amount
	 *            The amount to set. Must be bigger then 0.
	 */
	public void setAmount(int amount) {
		if (amount <= 0) {
			throw new IllegalArgumentException("Amount must be bigger then 0.");
		}

		if (item.getType() == ItemType.EQUIP && amount != 1) {
			throw new IllegalArgumentException(
					"Amount of equipments must be equal to 1");
		}

		this.amount = amount;
	}

	@Override
	public String toString() {
		return String.format("PlayerItem[accId: %d, itemId: %d, amount: %d]",
				account.getId(), item.getId(), amount);
	}
}
