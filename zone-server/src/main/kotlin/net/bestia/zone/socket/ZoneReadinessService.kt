package net.bestia.zone.socket

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

/**
 * Reusable "the zone is ready to accept players" gate. Startup work that must finish before any
 * client is allowed in (entity reload, and later world generation) runs before [markReady] is
 * called. The socket handshake consults [isReady] and rejects logins with `SERVER_NOT_READY`
 * until then — a clean rejection rather than a silent hang, and robust even if the socket has
 * already bound.
 */
@Service
class ZoneReadinessService {

  @Volatile
  private var ready = false

  fun isReady(): Boolean = ready

  fun markReady() {
    ready = true
    LOG.info { "Zone is ready - accepting client logins." }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
