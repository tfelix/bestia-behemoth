package net.bestia.model.domain;

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "player_items")
public class PlayerItems implements Serializable {

	private static final long serialVersionUID = 1L;
	
	/**
	 * Number of items inside the players inventory.
	 */
	private int amount;
}
