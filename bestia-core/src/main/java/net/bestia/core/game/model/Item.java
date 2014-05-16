package net.bestia.core.game.model;

import java.net.URL;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
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
	private String item_db_name;
	// name description
	private URL image;
	private int price;
	private ItemType type;
}
