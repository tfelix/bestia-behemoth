package net.bestia.api.bugreport

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.bestia.api.bus.ZoneClient
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

/** The zone's view of a reporter's master, kept as `server.json`. A report never fails for want of it. */
@Component
class ServerSnapshots(private val zone: ZoneClient) {

  private val json = jacksonObjectMapper()

  fun snapshotOf(accountId: Long): BugReportAttachment.Prepared {
    val content = try {
      val reply = zone.ask(accountId, ZoneQuery.BugReportSnapshot)
      (reply.answer as? ZoneAnswer.Snapshot)?.json ?: unavailable("the zone answered ${reply.status}")
    } catch (e: ResponseStatusException) {
      unavailable(e.reason ?: e.statusCode.toString())
    }

    return BugReportAttachment.Prepared(FILE_NAME, content.toByteArray())
  }

  private fun unavailable(reason: String): String {
    return json.writeValueAsString(mapOf("unavailable" to reason))
  }

  private companion object {
    const val FILE_NAME = "server.json"
  }
}
