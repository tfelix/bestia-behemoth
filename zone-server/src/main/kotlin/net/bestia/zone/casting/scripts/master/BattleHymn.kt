package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

/** Battle Hymn (`skills.yml` id 166): a song for more ATK and MATK. */
@Component
class BattleHymn : Song(StatusEffectId.SONG_BATTLE_HYMN)
