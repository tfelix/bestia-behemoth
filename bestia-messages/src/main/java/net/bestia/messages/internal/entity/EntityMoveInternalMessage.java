package net.bestia.messages.internal.entity;

import java.util.ArrayList;
import java.util.List;

import net.bestia.messages.entity.EntityMoveMessage;
import net.bestia.model.geometry.Point;

/**
 * In contrast to the {@link EntityMoveMessage} which is send from a client this
 * message is purely ment to be send internally from the server actor to another
 * actor to perform the movement. The internal path is represented differently
 * and thus makes it easier to use it without transforming it first.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class EntityMoveInternalMessage extends EntityMessage {
	
	private static final long serialVersionUID = 1L;
	
	private List<Point> path;

	public EntityMoveInternalMessage(long entityId, List<Point> path) {
		super(entityId);
		
		this.path = new ArrayList<>(path);
	}

	public List<Point> getPath() {
		return path;
	}
}
