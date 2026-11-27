package net.bestia.zone.companion.query

import net.bestia.bus.contract.ZoneAnswer
import net.bestia.zone.doomsday.DoomsdaySigns
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration

/** The world as the companion app shows it. Players learn of the doomsday no sooner than in the game. */
@Service
class WorldQueries(
  private val worldService: WorldService,
  private val doomsday: DoomsdaySigns,
  private val clock: Clock = Clock.systemUTC(),
) {

  fun world(): ZoneAnswer.WorldData {
    val record = worldService.record
    val doom = doomsday.known()

    return ZoneAnswer.WorldData(
      name = record.name,
      ageDays = Duration.between(record.createdAt, clock.instant()).toDays().toInt(),
      threat = doom?.first?.name,
      stage = doom?.second?.name,
    )
  }
}
