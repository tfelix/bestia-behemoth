package net.bestia.zoneserver.entity;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.bestia.model.domain.SpriteInfo;
import net.bestia.model.entity.InteractionType;
import net.bestia.model.geometry.CollisionShape;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.entity.traits.Interactable;
import net.bestia.zoneserver.entity.traits.Locatable;
import net.bestia.zoneserver.entity.traits.Visible;

public class ScriptEntity extends BaseEntity implements Locatable, Visible, Interactable {
	
	private static final long serialVersionUID = 1L;
	private CollisionShape shape;
	private SpriteInfo sprite;
	
	public ScriptEntity(CollisionShape shape, SpriteInfo sprite) {
		
		setShape(shape);
		setVisual(sprite);
	}

	@Override
	public float getMovementSpeed() {
		// can not move.
		return 0;
	}

	@Override
	public CollisionShape getShape() {
		return shape;
	}

	@Override
	public void setShape(CollisionShape shape) {
		
		this.shape = Objects.requireNonNull(shape);
	}

	@Override
	public boolean isVisible() {
		
		return true;
	}

	@Override
	public Set<InteractionType> getPossibleInteractions(Interactable interacter) {
		
		return Collections.emptySet();
	}

	@Override
	public Set<InteractionType> getInteractions() {
		
		return Collections.emptySet();
	}

	@Override
	public void triggerInteraction(InteractionType type, Interactable interactor) {
		// no op.
	}

	@Override
	public SpriteInfo getVisual() {
		
		return sprite;
	}

	@Override
	public void setVisual(SpriteInfo visual) {
		
		this.sprite = Objects.requireNonNull(visual);
	}

	@Override
	public Point getPosition() {
		
		return shape.getAnchor();
	}

	@Override
	public void setPosition(long x, long y) {
		
		shape = shape.moveByAnchor(x, y);
	}

	@Override
	public void moveTo(List<Point> path) {
		
		// no op.
	}
	
	public void onEnter(Locatable entity) {
		
	}
	
	public void onLeave(Locatable entity) {
		
	}
	
	public void onTick(List<Locatable> entities) {
		
	}
	
	public void onMoveInside(Locatable entity, List<Point> path) {
		
	}
	
	public void onCreate() {
		
	}
	
	public void onDestroy() {
		
	}
}
