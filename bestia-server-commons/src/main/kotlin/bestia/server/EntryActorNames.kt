package bestia.server

/**
 * Class contains toplevel actor names so the user can address them with
 * messages.
 *
 * @author Thomas Felix
 */
object EntryActorNames {
  @JvmField
  val ENTITY_MANAGER = "entity"

  @JvmField
  val SHARD_ENTITY = "entityShard"

  @JvmField
  val SHARD_CONNECTION = "connectionShard"
}