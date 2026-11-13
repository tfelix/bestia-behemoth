package net.bestia.zone.battle.status.scripts

import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

/** `status_effects.yml` id 239 (`SONG_WANDERERS_TUNE`): the Bard plays Wanderer's Tune, see [WanderersTuneHeard]. */
@Component
class WanderersTuneSong(aoi: EntityAOIService, songcraft: Songcraft) : SongScript(aoi, songcraft) {

  override val granted = StatusEffectId.WANDERERS_TUNE

  override val baseSeconds = TUNE_SECONDS

  private companion object {
    /** Made for a long road, not a fight. */
    const val TUNE_SECONDS = 600.0
  }
}
