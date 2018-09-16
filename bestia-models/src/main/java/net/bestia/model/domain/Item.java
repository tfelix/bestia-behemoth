package net.bestia.model.domain;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Items can be added to a players inventory. They can be used, traded, sold,
 * dropped etc.
 * 
 * @author Thomas Felix
 *
 */
@Entity
@Table(name = "items")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Item implements Serializable {

	@Transient
	private static final long serialVersionUID = 1L;

	@Id
	private int id;

	@Column(name = "item_db_name", unique = true, nullable = false)
	@JsonProperty("idbn")
	private String itemDbName;

	@Column(nullable = false)
	@JsonProperty("img")
	private String image;

	@JsonIgnore
	@Column(nullable = false)
	private int price;

	/**
	 * Weight of the item. The rule is: 100gr = 1 weight unit.
	 */
	@Column(nullable = false)
	@JsonProperty("w")
	private int weight;

	@Enumerated(EnumType.STRING)
	@JsonProperty("t")
	private ItemType type;

	private EquipmentSlot usedSlot;
	
	@JsonProperty("i")
	private String indicator;
	
	@JsonProperty("lv")
	private int level;

	/**
	 * Maybe query a script for the range.
	 */
	private int usableDefaultRange = 0;

	public int getId() {
		return id;
	}

	public String getItemDbName() {
		return itemDbName;
	}

	public String getImage() {
		return image;
	}

	public int getPrice() {
		return price;
	}

	public ItemType getType() {
		return type;
	}

	public String getIndicator() {
		return indicator;
	}
	
	public int getWeight() {
		return weight;
	}
	
	public int getLevel() {
		return level;
	}
	
	public int getUsableDefaultRange() {
		return usableDefaultRange;
	}

	public EquipmentSlot getUsedSlot() {
		return usedSlot;
	}

	public void setUsedSlot(EquipmentSlot usedSlot) {
		this.usedSlot = usedSlot;
	}

	@Override
	public String toString() {
		return String.format("Item[dbName: %s, id: %d, type: %s]", itemDbName, id, type);
	}
}
