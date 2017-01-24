package net.bestia.zoneserver.entity;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import net.bestia.model.domain.Attack;
import net.bestia.model.domain.SpriteInfo;
import net.bestia.model.geometry.Point;
import net.bestia.model.geometry.Rect;
import net.bestia.model.map.Map;
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

	private SpriteInfo visual;

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

	/**
	 * Returns the sight range of the entity. This method is very important, for
	 * the AI to be able to "see" other entities and react upon them.
	 * 
	 * @return The range {@link Rect} of the sight range originating from this
	 *         entity.
	 */
	public Rect getSightRect() {
		final Point pos = getPosition();
		final Rect sightRect = new Rect(pos.getX() - Map.SIGHT_RANGE,
				pos.getY() - Map.SIGHT_RANGE,
				pos.getX() + Map.SIGHT_RANGE,
				pos.getY() + Map.SIGHT_RANGE);
		return sightRect;
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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setPosition(long x, long y) {
		// TODO Auto-generated method stub

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
		// No op.
	}
	
	@Override
	public int getLevel() {
		return 1;
	}
}
