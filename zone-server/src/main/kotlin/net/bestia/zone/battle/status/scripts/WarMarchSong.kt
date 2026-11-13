package net.bestia.zone.battle.status.scripts

import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

/** `status_effects.yml` id 235 (`SONG_WAR_MARCH`): the Bard plays War March, see [WarMarchHeard]. */
@Component
class WarMarchSong(aoi: EntityAOIService, songcraft: Songcraft) : SongScript(aoi, songcraft) {

  override val granted = StatusEffectId.WAR_MARCH

  override val baseSeconds = SONG_SECONDS
}
