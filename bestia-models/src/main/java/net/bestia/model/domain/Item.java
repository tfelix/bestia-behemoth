package net.bestia.model.domain;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Items can be added to a players inventory. They can be used, traded, sold, dropped etc.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Entity
@Table(name = "items")
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
	private int weight;

	@Enumerated(EnumType.STRING)
	@JsonProperty("t")
	private ItemType type;

	@OneToMany(fetch = FetchType.LAZY, mappedBy = "item")
	@JsonIgnore
	private Set<PlayerItem> playerItems = new HashSet<>(0);

	public int getId() {
		return id;
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

	public int getWeight() {
		return weight;
	}
}
