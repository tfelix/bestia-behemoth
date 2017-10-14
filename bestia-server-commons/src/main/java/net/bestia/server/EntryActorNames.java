package net.bestia.server;

/**
 * Class contains toplevel actor names so the user can address them with
 * messages.
 * 
 * @author Thomas Felix
 *
 */
public final class EntryActorNames {

	public static final String ENTITY_MANAGER = "entity";
	
	public static final String SHARD_ENTITY = "entityShardManager";
	
	private EntryActorNames() {
		// no op.
	}
}
