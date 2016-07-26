package net.bestia.zoneserver.ecs.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.artemis.Aspect;
import com.artemis.BaseEntitySystem;
import com.artemis.Entity;
import com.artemis.utils.Bag;

/**
 * Own implementation of UuidManager since the native manager does get called
 * too early, before we can send the entity discard message (both mechanisms
 * rely on entity remove).
 * 
 * @author Thomas
 *
 */
public class UuidManager extends BaseEntitySystem {
	
	private class DeleteRecord {
		final public UUID uuid;
		final public int entityId;
		
		public DeleteRecord(UUID uuid, int entityId) {
			this.uuid = uuid;
			this.entityId = entityId;
		}
	}

	private final Map<UUID, Entity> uuidToEntity;
	private final Bag<UUID> entityToUuid;
	
	private final Bag<DeleteRecord> dereferedDeleted;

	public UuidManager() {
		super(Aspect.all());
		this.uuidToEntity = new HashMap<UUID, Entity>();
		this.entityToUuid = new Bag<UUID>();
		
		this.dereferedDeleted = new Bag<>();

		// Dont iterate.
		//setEnabled(false);
	}

	@Override
	protected void removed(int entityId) {
		final UUID uuid = entityToUuid.safeGet(entityId);
		if (uuid == null) {
			return;
		}

		dereferedDeleted.add(new DeleteRecord(uuid, entityId));
	}

	public void updatedUuid(Entity e, UUID newUuid) {
		setUuid(e, newUuid);
	}

	public Entity getEntity(UUID uuid) {
		return uuidToEntity.get(uuid);
	}

	public UUID getUuid(Entity e) {
		UUID uuid = entityToUuid.safeGet(e.getId());
		if (uuid == null) {
			uuid = UUID.randomUUID();
			setUuid(e, uuid);
		}

		return uuid;
	}

	public void setUuid(Entity e, UUID newUuid) {
		UUID oldUuid = entityToUuid.safeGet(e.getId());
		if (oldUuid != null)
			uuidToEntity.remove(oldUuid);

		uuidToEntity.put(newUuid, e);
		entityToUuid.set(e.getId(), newUuid);
	}

	@Override
	protected void processSystem() {
		// no op.
	}
	
	/**
	 * Cleanup the entities which where deleted.
	 */
	@Override
	protected void end() {
		super.end();
		
		for(int i = 0; i < dereferedDeleted.size(); i++) {
			final DeleteRecord delRec = dereferedDeleted.get(i);
			final Entity oldEntity = uuidToEntity.get(delRec.uuid);
			if (oldEntity != null && oldEntity.getId() == delRec.entityId) {
				uuidToEntity.remove(delRec.uuid);
			}

			entityToUuid.set(delRec.entityId, null);
		}
		
		dereferedDeleted.clear();
	}
}
