package net.bestia.bus

/** Every Valkey key and channel the bus uses. The ACL file in `zone-server/valkey/users.acl` grants by these. */
object BusKeys {

  /** The stream every zone event goes to. */
  const val EVENTS = "events"

  /** The consumer group that turns events into Web Push notifications. */
  const val WEBPUSH_GROUP = "webpush"

  /** Set while a new world is being born, after the last one ended. */
  const val WORLD_STATE = "world:state"

  /** How the last world ended, see [WorldRebirth.lastWorld]. */
  const val LAST_WORLD = "world:last"

  /** The stream the login server appends its kick orders to. */
  const val KICKS = "kicks"

  /** Every zone node reads [KICKS] in a group of its own, because every node has to see every kick. */
  fun kickGroup(nodeId: String): String {
    return "kicks-$nodeId"
  }

  /** The channel the api-server sends GM actions to, for the login server to carry out. */
  const val LOGIN_COMMANDS = "login:commands"

  fun loginReplies(instanceId: String): String {
    return "login:replies:$instanceId"
  }

  /** Taken by the one login server that acts on a command, when several are subscribed. */
  fun loginClaim(correlationId: String): String {
    return "login:claim:$correlationId"
  }

  fun requests(nodeId: String): String {
    return "zone:$nodeId:requests"
  }

  fun replies(instanceId: String): String {
    return "api:$instanceId:replies"
  }

  fun directory(accountId: Long): String {
    return "dir:account:$accountId"
  }

  /** Set while an api-server holds an open companion socket of [accountId]. */
  fun appOpen(accountId: Long): String {
    return "app:account:$accountId"
  }

  fun idempotency(accountId: Long, key: String): String {
    return "bus:idem:$accountId:$key"
  }
}
