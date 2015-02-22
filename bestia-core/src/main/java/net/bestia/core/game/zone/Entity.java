package net.bestia.core.game.zone;

import java.time.Duration;
import java.util.Date;
import java.util.Hashtable;

/**
 * A basic entity is placeable on a map. It show no intelligent behavior nor can
 * it change by any means of KI (Change via a script, spawn-, despawn are
 * possible though). A entity has a bounding box usually rectangula but any
 * shape is possible. However it needs to return a bounding box in order to get
 * sorted into the entity manager.
 * 
 * @author Thomas
 *
 */
public class Entity {

	private static final int REQUEST_REMOVE = 1;

	public interface EntityTrigger {
		public void onStart();

		public void onEnd();

		public void onTouch(Entity toucher);

		public void onTouchLeave(Entity leaver);

		public void onInteract(Entity actor);

		public void onEntitySpawn(Entity spawn);

		public void onZonePropertyChanged(Property newProperty);

		public void onTick();
	}

	/**
	 * Each entity has its own unique id.
	 */
	private long id;

	/**
	 * Sprite name. Can be null if no sprite associated with this entity.
	 */
	private String sprite;

	/**
	 * Flag if this entity works as a collider. Movement through this entity
	 * wont be possible then.
	 */
	private boolean isColliding;

	/**
	 * An entity might be owned by another. This entity will get removed if the
	 * parent dies. If it has no parent then this is null.
	 */
	private Entity child;

	/**
	 * Location of the entity on the map.
	 */
	private Point location;

	private Duration duration;

	/**
	 * Shape of the entity used for calculating the collision.
	 */
	private CollisionShape shape;

	private String scriptFile;

	private Date spawnTime;

	/**
	 * Returns the bounding box of the entity. It has local coordinates. If the
	 * bounding box is a rectangle it left top coordinate will always be 0,0
	 * regardless where on the map the entity currently is.
	 * 
	 * @return Bounding box.
	 */
	public Dimension getBoundingBox() {
		return shape.getBoundingBox();
	}

	public Long getId() {
		return id;
	}

	/**
	 * Returns the spritesheet of the entity.
	 * 
	 * @return
	 */
	public String getSprite() {
		return sprite;
	}

	/**
	 * Returns if the entity serves as a collider. However the entity might sill
	 * have a form or dimension. So calling {@link getCollisionShape()} will
	 * return a collision shape.
	 * 
	 * @return If the entity serves as a collider.
	 */
	public boolean isColliding() {
		return isColliding;
	}

	/**
	 * Return the coordinates of the entity in global space.
	 * 
	 * @return
	 */
	public Point getLocation() {
		return location;
	}

	/**
	 * Called by the zone after the Entity has been added. In this method a
	 * entity script might get called and can attach to various event handler in
	 * the zone to get notified about various events.
	 */
	public void onStart(Zone zone) {

		spawnTime = new Date();

		if (duration != null) {
			zone.scheduleNotify(duration, this, REQUEST_REMOVE);
		}
		// TODO Entity script callen.
	}

	/**
	 * Gets called when the entity itself is removed from the zone. Here should
	 * be done some cleaning.
	 */
	public void onEnd(Zone zone) {
		if (child != null) {
			zone.removeEntity(child);
		}
	}

	public void onTouch(Entity toucher) {

	}

	public void onTouchLeave(Entity leaver) {

	}

	public void onInteract(Entity actor) {

	}

	public void onEntitySpawn(Entity spawnEntity) {

	}

	public void onEntityRemove(Entity removeEntity) {

	}

	public void onZonePropertyChanged(Property newProperty, Zone zone) {

	}

	public void onTick(Zone zone, int requestCode) {
		if (requestCode == REQUEST_REMOVE) {
			zone.removeEntity(this);
			return;
		}
		// TODO das Entity script hier callen.
	}

	public CollisionShape getCollision() {
		// TODO Auto-generated method stub
		return null;
	}

	// spawn-time
	// end-time oder null wenn permanent.

	// HP? Wiederstand. Das muss irgendwie von Bestia separiert werden.

	// Statusveränderungen.

	// Kann animationen triggern.

	// Ändert sich etwas wichtiges an der Entity? Benachrichtige die Zone, finde
	// Spieler in der Nähe und informiere sie.

}
