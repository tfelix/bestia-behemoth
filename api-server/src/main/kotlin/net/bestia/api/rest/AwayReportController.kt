package net.bestia.api.rest

import net.bestia.api.auth.CompanionAuthFilter.Companion.ACCOUNT_ID
import net.bestia.api.bus.ZoneClient
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RestController

/** What each of a master's bestias did since the master last left the world, as facts the app puts in words. */
@RestController
class AwayReportController(
  private val zone: ZoneClient,
) {

  @GetMapping("/v1/companion/masters/{masterId}/away-report")
  fun awayReport(@RequestAttribute(ACCOUNT_ID) accountId: Long, @PathVariable masterId: Long): ZoneAnswer.AwayReport {
    return zone.ask(accountId, ZoneQuery.AwayReport(masterId)).answerOrThrow<ZoneAnswer.AwayReport>()
  }
}
