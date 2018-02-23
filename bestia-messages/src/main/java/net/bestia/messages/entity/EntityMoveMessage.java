package net.bestia.messages.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import bestia.model.geometry.Point;

/**
 * In contrast to the {@link EntityMoveMessage} which is send from a client this
 * message is purely ment to be send internally from the server actor to another
 * actor to perform the movement. The internal path is represented differently
 * and thus makes it easier to use it without transforming it first.
 * 
 * @author Thomas Felix
 *
 */
public class EntityMoveMessage {

	private final List<Point> path;

	public EntityMoveMessage(List<Point> path) {
		this.path = Collections.unmodifiableList(new ArrayList<>(path));
	}

	public List<Point> getPath() {
		return path;
	}

	@Override
	public String toString() {
		return String.format("EntityMoveInternalMessage[path: %s]", path.toString());
	}
}
