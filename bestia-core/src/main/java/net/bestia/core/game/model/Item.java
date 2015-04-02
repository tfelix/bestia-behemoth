package net.bestia.core.game.model;

import java.net.URL;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

//@Entity
public class Item {
	
	public enum ItemType {
		USABLE_NORMAL,
		USABLE_BATTLE,
		USABLE_ALL,
		EQUIP,
		ETC,
		QUEST
	}
	
	@Id
	private int id;
	@Column(name = "item_db_name")
	private String itemDbName;
	// name description
	private URL image;
	private int price;
	private ItemType type;
	
	public int getId() {
		return id;
	}
	
	public URL getImage() {
		return image;
	}
	
	public int getPrice() {
		return price;
	}
	
	public ItemType getType() {
		return type;
	}
}
