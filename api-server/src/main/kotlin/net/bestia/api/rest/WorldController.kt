package net.bestia.api.rest

import net.bestia.api.auth.CompanionAuthFilter.Companion.ACCOUNT_ID
import net.bestia.api.bus.ZoneClient
import net.bestia.bus.WorldRebirth
import net.bestia.bus.contract.LastWorld
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * How the world fares. While a new world is born the zone is down, so only the bus answers: that a rebirth is
 * underway, and how the last world ended.
 */
@RestController
@RequestMapping("/v1/companion/world")
class WorldController(
  private val zone: ZoneClient,
  private val rebirth: WorldRebirth,
) {

  /** [threat] is a doomsday kind and [stage] its stage, both null while the world shows no doomsday yet. */
  data class CurrentWorld(val name: String, val ageDays: Int, val threat: String?, val stage: String?)

  /** [current] is null during a [rebirth]; [last] is null until a world has ended. */
  data class WorldState(val rebirth: Boolean, val current: CurrentWorld?, val last: LastWorld?)

  @GetMapping
  fun world(@RequestAttribute(ACCOUNT_ID) accountId: Long): WorldState {
    val last = rebirth.lastWorld()
    if (rebirth.isUnderway()) {
      return WorldState(rebirth = true, current = null, last = last)
    }

    val data = zone.ask(accountId, ZoneQuery.World).answerOrThrow<ZoneAnswer.WorldData>()
    return WorldState(rebirth = false, current = CurrentWorld(data.name, data.ageDays, data.threat, data.stage), last = last)
  }
}
