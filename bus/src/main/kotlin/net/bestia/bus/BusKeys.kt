package net.bestia.bus

/** Every Valkey key and channel the bus uses. The ACL file in `zone-server/valkey/users.acl` grants by these. */
object BusKeys {

  /** The stream every zone event goes to. */
  const val EVENTS = "events"

  /** The consumer group that turns events into Web Push notifications. */
  const val WEBPUSH_GROUP = "webpush"

  /** The stream the login server appends its kick orders to. */
  const val KICKS = "kicks"

  /** Every zone node reads [KICKS] in a group of its own, because every node has to see every kick. */
  fun kickGroup(nodeId: String): String {
    return "kicks-$nodeId"
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

  fun idempotency(accountId: Long, key: String): String {
    return "bus:idem:$accountId:$key"
  }
}
