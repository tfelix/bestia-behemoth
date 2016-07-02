package net.bestia.zoneserver.ecs.component;

import java.io.Serializable;

import com.artemis.Component;

public class Item extends Component implements Serializable {

	private static final long serialVersionUID = 1L;
	
	public int itemId;
	public int amount;
	public int playerItemId = -1;
	
}
