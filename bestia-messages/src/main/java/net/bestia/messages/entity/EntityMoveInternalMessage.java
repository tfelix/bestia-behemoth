package net.bestia.messages.entity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import net.bestia.messages.EntityMessage;
import net.bestia.messages.Message;
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
public class EntityMoveInternalMessage extends Message implements EntityMessage, Serializable {

	private static final long serialVersionUID = 1L;

	private final List<Point> path;
	private final long entityId;

	public EntityMoveInternalMessage(long entityId, List<Point> path) {
		
		this.entityId = entityId;
		this.path = new ArrayList<>(path);
	}

	public List<Point> getPath() {
		return path;
	}

	@Override
	public String toString() {
		return String.format("EntityMoveInternalMessage[eeid: %d, path: %s]", getEntityId(), path.toString());
	}

	@Override
	public long getEntityId() {
		return entityId;
	}
}