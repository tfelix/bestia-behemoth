package net.bestia.zone.battle.status.scripts

import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

/** `status_effects.yml` id 233 (`SONG_BATTLE_HYMN`): the Bard plays Battle Hymn, see [BattleHymnHeard]. */
@Component
class BattleHymnSong(aoi: EntityAOIService, songcraft: Songcraft) : SongScript(aoi, songcraft) {

  override val granted = StatusEffectId.BATTLE_HYMN

  override val baseSeconds = SONG_SECONDS
}
