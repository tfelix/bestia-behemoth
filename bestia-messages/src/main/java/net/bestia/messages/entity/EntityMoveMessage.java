package net.bestia.messages.entity;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.EntityJsonMessage;
import net.bestia.model.geometry.Point;
import net.bestia.model.map.Walkspeed;

/**
 * <p>
 * This message is send from the server to all clients as soon as a movement is
 * started.
 * </p>
 * <p>
 * It is also send from the client to the server in order to perform a movement.
 * In this case the speed is ignored since it is determined by the server.
 * </p>
 * <p>
 * It contains the path of the bestia as well as the speed. As long as nothing
 * changes along the path no further update is send and the client can use this
 * information to interpolate the movement of the entity. If a new
 * {@link EntityMoveMessage} is send by the server it takes precedence over the
 * old one.
 * </p>
 * 
 * @author Thomas Felix
 *
 */
public class EntityMoveMessage extends EntityJsonMessage {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "entity.move";

	@JsonProperty("pX")
	private List<Long> cordsX;

	@JsonProperty("pY")
	private List<Long> cordsY;

	@JsonProperty("w")
	private float walkspeed;

	/**
	 * This is the time latency of the moving entity. For server entities this
	 * is 0, but for player controlled entities it is their latency.
	 */
	private int delta;

	/**
	 * This is the latency of the player receiving this update.
	 */
	private int latency;

	public EntityMoveMessage() {
		// no op.
	}

	/**
	 * Helper ctor if the receiving account id is not known upon creation of
	 * this message.
	 * 
	 * @param eid
	 * @param path
	 * @param walkspeed
	 */
	public EntityMoveMessage(long eid, List<Point> path, Walkspeed walkspeed) {
		this(0, eid, path, walkspeed);
	}

	public EntityMoveMessage(long accId, long eid, List<Point> path, Walkspeed walkspeed) {
		super(accId, eid);

		this.walkspeed = walkspeed.getSpeed();

		cordsX = new ArrayList<>(path.size());
		cordsY = new ArrayList<>(path.size());

		path.forEach(x -> {
			cordsX.add(x.getX());
			cordsY.add(x.getY());
		});
	}

	public EntityMoveMessage(long entityId, List<Point> path) {
		this(entityId, path, Walkspeed.ZERO);
	}
	
	public void setDelta(int delta) {
		this.delta = delta;
	}
	
	public int getDelta() {
		return delta;
	}
	
	public void setLatency(int latency) {
		this.latency = latency;
	}
	
	public int getLatency() {
		return latency;
	}

	public List<Long> getCordsX() {
		return cordsX;
	}

	public void setCordsX(List<Long> cordsX) {
		this.cordsX = cordsX;
	}

	public List<Long> getCordsY() {
		return cordsY;
	}

	public void setCordsY(List<Long> cordsY) {
		this.cordsY = cordsY;
	}

	public float getWalkspeed() {
		return walkspeed;
	}

	public void setWalkspeed(float walkspeed) {
		this.walkspeed = walkspeed;
	}

	/**
	 * Turns the list of coordiantes into a array of points.
	 * 
	 * @return
	 */
	public List<Point> getPath() {
		if (cordsX.size() != cordsY.size()) {
			throw new IllegalStateException("Size of the coordiante arrays does not match.");
		}

		final List<Point> patch = new ArrayList<>(cordsX.size());

		for (int i = 0; i < cordsX.size(); i++) {
			patch.add(new Point(cordsX.get(i), cordsY.get(i)));
		}

		return patch;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String toString() {
		return String.format(
				"EntityMoveMessage[eid: %d, pathX: %s, pathY: %s, walkspeed: %f]", getEntityId(),
				cordsX.toString(), cordsY.toString(), walkspeed);
	}

	@Override
	public EntityMoveMessage createNewInstance(long accountId) {
		return new EntityMoveMessage(accountId, getEntityId(), getPath(), Walkspeed.fromFloat(walkspeed));
	}
}
