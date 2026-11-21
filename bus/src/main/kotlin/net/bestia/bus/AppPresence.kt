package net.bestia.bus

/**
 * Which accounts have a companion app open. The api-servers hold the sockets and renew the marks; a zone reads them to
 * decide whether a whisper to a player who is not in the game has anywhere to go. A mark lapses on its own, so an
 * api-server that dies takes its accounts out by itself.
 */
interface AppPresence {

  fun markOpen(accountIds: Collection<Long>, ttlSeconds: Long)

  fun isOpen(accountId: Long): Boolean
}
