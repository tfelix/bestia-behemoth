package net.bestia.messages.map;


import net.bestia.messages.InputMessage;
import net.bestia.model.domain.Location;

/**
 * The ECS is adviced to move the given entity on the map. This is an internal
 * message.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapMoveMessage extends InputMessage {

	private static final long serialVersionUID = 1L;

	public final static String MESSAGE_ID = "map.move";
	
	private Location target;
	
	public MapMoveMessage() {
		
	}
	
	public MapMoveMessage(long accId, int playerBestiaId) {
		super(accId, playerBestiaId);
	}
	
	public void setTarget(Location target) {
		if(target == null) {
			throw new IllegalArgumentException("Target can not be null.");
		}
		this.target = target;
	}
	
	public Location getTarget() {
		return target;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
}
