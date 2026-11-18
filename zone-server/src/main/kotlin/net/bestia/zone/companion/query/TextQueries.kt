package net.bestia.zone.companion.query

import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusReply.Status
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import net.bestia.zone.text.WorldTexts
import org.springframework.stereotype.Component

/** Serves the written texts of this world to the api-server. */
@Component
class TextQueries(private val texts: WorldTexts) {

  fun text(correlationId: String, query: ZoneQuery.Text): BusReply {
    if (!WorldTexts.isTextId(query.textId) || !WorldTexts.isLocale(query.locale)) {
      return BusReply.of(correlationId, Status.INVALID, "not a text id and a locale")
    }

    val body = texts.bodyOf(query.textId, query.locale) ?: return BusReply.of(correlationId, Status.NOT_FOUND)

    return BusReply.ok(correlationId, ZoneAnswer.Text(query.textId, body.locale, body.text))
  }
}
