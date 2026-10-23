package net.bestia.zone.companion.query

import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusReply.Status
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import net.bestia.zone.guild.persistence.GuildEmblemRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Serves a guild's emblem to the api-server, which keeps it for good: the hash names one picture forever. */
@Component
class GuildEmblemQueries(private val emblems: GuildEmblemRepository) {

  @Transactional(readOnly = true)
  fun emblem(correlationId: String, query: ZoneQuery.GuildEmblem): BusReply {
    if (!HASH.matches(query.hash)) {
      return BusReply.of(correlationId, Status.INVALID, "not an emblem hash")
    }

    val emblem = emblems.findFirstByHash(query.hash) ?: return BusReply.of(correlationId, Status.NOT_FOUND)
    return BusReply.ok(correlationId, ZoneAnswer.Emblem(emblem.hash), emblem.png)
  }

  private companion object {
    val HASH = Regex("^[0-9a-f]{64}$")
  }
}
