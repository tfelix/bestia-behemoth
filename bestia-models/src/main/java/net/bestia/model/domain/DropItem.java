package net.bestia.model.domain;

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

@Entity
@Table(name = "drop_items")
public class DropItem implements Serializable {

	@Transient
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(unique = true, nullable = false)
	private int id;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "ITEM_ID", nullable = false)
	private Item item;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "BESTIA_ID", nullable = false)
	private Bestia bestia;

	private float probability;

	/**
	 * Std. ctor for Hibernate.
	 */
	public DropItem() {
		// no op.
	}

	/**
	 * Ctor.
	 * 
	 * @param item
	 *            The dropped item.
	 * @param bestia
	 *            The bestia who drops this item.
	 * @param probability
	 *            The probability of the item drop.
	 */
	public DropItem(Item item, Bestia bestia, float probability) {
		setItem(item);
		setBestia(bestia);
		setProbability(probability);
	}

	public void setBestia(Bestia bestia) {
		if (bestia == null) {
			throw new IllegalArgumentException("Bestia can not be null.");
		}

		this.bestia = bestia;
	}

	public Bestia getBestia() {
		return bestia;
	}

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

		this.item = item;
	}

	@Override
	public String toString() {
		return String.format("DropItem[bestiaId: %d, itemId: %d, prob: %f]",
				bestia.getId(),
				item.getId(),
				getProbability());
	}

	/**
	 * The probability of an item drop. The value is in percentage from 0-100%.
	 * 
	 * @return The probability from 0 to 100.
	 */
	public float getProbability() {
		return probability;
	}

	/**
	 * The probability of an item drop. The value is in percentage from 0-100%.
	 * 
	 * @param probability
	 *            The probability of the item drop between 0 and 100.
	 */
	public void setProbability(float probability) {
		if (probability < 0 || probability > 100) {
			throw new IllegalArgumentException("Probability must be between 0 and 100.");
		}
		this.probability = probability;
	}
}
