package net.bestia.bus

/**
 * Which zone node holds an account that is in the world. An entry lives only as long as its node keeps renewing
 * it, so a node that dies takes its accounts out of the directory by itself.
 */
interface Directory {

  fun register(accountIds: Collection<Long>, nodeId: String, ttlSeconds: Long)

  fun remove(accountId: Long)

  /** The node holding [accountId], or null when the account is not in the world. */
  fun nodeOf(accountId: Long): String?
}
