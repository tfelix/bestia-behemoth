package net.bestia.zone.breeding

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.time.BestiaClock
import net.bestia.zone.world.time.Season
import org.springframework.stereotype.Component

/** The season a farm has right now: south of the equator it is the other one. */
@Component
class SeasonAtFarm(
  private val clock: BestiaClock,
  private val worldService: WorldService,
) {

  fun at(farmAt: Vec3L): Season {
    val config = worldService.config
    val northwards = farmAt.y * config.voxelSize / config.heightMetres

    return Season.at(clock.now().yearProgress, northwards)
  }
}
