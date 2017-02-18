package net.bestia.messages.map;

import java.util.Objects;

import net.bestia.messages.JsonMessage;
import net.bestia.model.geometry.Point;

/**
 * The ECS is adviced to move the given entity on the map. This is an internal
 * message.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class MapMoveMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;

	public final static String MESSAGE_ID = "map.move";

	private Point target;

	/**
	 * Ctor.
	 */
	protected MapMoveMessage() {
		// no op.
	}

	/**
	 * Ctor. With account ID and target point to move the active bestia to.
	 * 
	 * @param accId
	 *            Account id
	 * @param target
	 *            The target to move the active bestia from this account.
	 */
	public MapMoveMessage(long accId, Point target) {
		super(accId);
		
		this.target = Objects.requireNonNull(target);
	}

	/**
	 * Sets the position to which the bestia will be moved.
	 * 
	 * @param target
	 *            The position to move the bestia to.
	 */
	public void setTarget(Point target) {
		this.target = Objects.requireNonNull(target);
	}

	/**
	 * Gets the point on which the bestia should be moved.
	 * 
	 * @return The point to which the bestia is moved.
	 */
	public Point getTarget() {
		return target;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
	
	@Override
	public String toString() {
		return String.format("MapMoveMesasge[accId: %d, target: %s]", getAccountId(), getTarget().toString());
	}

	@Override
	public MapMoveMessage createNewInstance(long accountId) {
		return new MapMoveMessage(accountId, target);
	}
}
