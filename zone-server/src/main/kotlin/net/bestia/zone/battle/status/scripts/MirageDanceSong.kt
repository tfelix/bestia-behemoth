package net.bestia.zone.battle.status.scripts

import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

/** `status_effects.yml` id 237 (`SONG_MIRAGE_DANCE`): the Bard plays Mirage Dance, see [MirageDanceHeard]. */
@Component
class MirageDanceSong(aoi: EntityAOIService, songcraft: Songcraft) : SongScript(aoi, songcraft) {

  override val granted = StatusEffectId.MIRAGE_DANCE

  override val baseSeconds = SONG_SECONDS
}
