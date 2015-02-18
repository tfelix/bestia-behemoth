package net.bestia.core.game.zone;

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
	private Entity owner;
	
	/**
	 * Location of the entity on the map.
	 */
	private Point location;
	
	/**
	 * Shape of the entity used for calculating the collision.
	 */
	private CollisionShape shape;

	// duration
	// spawn-time
	// end-time oder null wenn permanent.

	// HP? Wiederstand. Das muss irgendwie von Bestia separiert werden.

	// Statusveränderungen.

	// Kann animationen triggern.

	// Ändert sich etwas wichtiges an der Entity? Benachrichtige die Zone, finde
	// Spieler in der Nähe und informiere sie.

	public void getBoundingBox() {
		
		

	}
}
