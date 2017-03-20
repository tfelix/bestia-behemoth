package net.bestia.zoneserver.entity;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import net.bestia.messages.entity.EntityPositionMessage;
import net.bestia.model.domain.Attack;
import net.bestia.model.domain.SpriteInfo;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.entity.traits.Attackable;
import net.bestia.zoneserver.entity.traits.Interactable;
import net.bestia.zoneserver.entity.traits.Locatable;
import net.bestia.zoneserver.entity.traits.Visible;

/**
 * This is like {@link BaseEntity} also a very primitive entity. It may be used
 * for the normal on maps used entities apart from this it only holds some
 * placeholder method implementation which might be very well changed inside
 * child implementation. Until a use case is found for a {@link RessourceEntity}
 * it only exists as an abstract class. Normally other resources entities are
 * inherited from this class and implement the specific behaviour.
 * 
 * @author Thomas
 *
 */
public abstract class ResourceEntity extends BaseEntity implements Locatable, Visible, Interactable, Attackable {

	private static final long serialVersionUID = 1L;

	private int level = 1;
	private SpriteInfo visual;
	private Point position = new Point(0, 0);

	public ResourceEntity() {

		this.visual = SpriteInfo.placeholder();
	}

	public ResourceEntity(SpriteInfo visual) {

		setVisual(visual);
	}

	/**
	 * Helper for getting the x coordinate from the current position.
	 * 
	 * @return Current X position in the world.
	 */
	public long getX() {
		return getPosition().getX();
	}

	/**
	 * Helper for getting the y coordinate from the current position.
	 * 
	 * @return Current Y position in the world.
	 */
	public long getY() {
		return getPosition().getY();
	}

	public void setLevel(int level) {
		this.level = level;
	}

	@Override
	public SpriteInfo getVisual() {
		return visual;
	}

	@Override
	public void setVisual(SpriteInfo visual) {
		this.visual = Objects.requireNonNull(visual);
	}

	@Override
	public Point getPosition() {
		return position;
	}

	@Override
	public void setPosition(long x, long y) {
		position = new Point(x, y);

		// Send an update to the server system to check for script trigger etc.
		final EntityPositionMessage epmsg = new EntityPositionMessage(getId(), x, y);
		getContext().sendMessage(epmsg);
	}

	/**
	 * A simple resource can not attack on its own.
	 */
	@Override
	public List<Attack> getAttacks() {
		return Collections.emptyList();
	}

	/**
	 * A simple resource can not move on its own.
	 */
	@Override
	public void moveTo(List<Point> path) {
		// No op. Can not move.
	}

	/**
	 * Removes the entity from the whole system. Since we lack of access to the
	 * spring services in here we need to communicate this further up the chain.
	 * This needs refactoring as the whole entity system is currently flawed.
	 */
	@Override
	public void kill() {
		getContext().entityRemoved(getId());
	}

	@Override
	public int getLevel() {
		return level;
	}
}
