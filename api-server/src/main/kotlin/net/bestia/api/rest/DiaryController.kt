package net.bestia.api.rest

import net.bestia.api.auth.CompanionAuthFilter.Companion.ACCOUNT_ID
import net.bestia.api.bus.ZoneClient
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RestController

/** What a master's bestias did lately, newest first. Lines are kept for a week by default. */
@RestController
class DiaryController(
  private val zone: ZoneClient,
) {

  @GetMapping("/v1/companion/masters/{masterId}/diary")
  fun diary(@RequestAttribute(ACCOUNT_ID) accountId: Long, @PathVariable masterId: Long): List<ZoneAnswer.Diary.DiaryEntry> {
    return zone.ask(accountId, ZoneQuery.Diary(masterId)).answerOrThrow<ZoneAnswer.Diary>().lines
  }
}
