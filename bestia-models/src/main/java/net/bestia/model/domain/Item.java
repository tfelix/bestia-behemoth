package net.bestia.model.domain;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Items can be added to a players inventory. They can be used, traded, sold,
 * dropped etc.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Entity
@Table(name = "items")
public class Item implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	private int id;

	@Column(name = "item_db_name", unique = true, nullable = false)
	private String itemDbName;

	@Column(nullable = false)
	private String image;
	
	private int price;

	@Enumerated(EnumType.STRING)
	private ItemType type;

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
}
