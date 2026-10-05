package net.bestia.zone.account.authentication

import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/** The ids of tokens already used, each kept until its token expires and could not be used anyway. */
class SingleUseTokenIds {

  private val usedUntil = ConcurrentHashMap<String, Long>()

  /** False when [tokenId] was used before. */
  fun acceptOnce(tokenId: String, expiresAt: Date): Boolean {
    val now = System.currentTimeMillis()
    usedUntil.values.removeIf { it < now }

    return usedUntil.putIfAbsent(tokenId, expiresAt.time) == null
  }
}
