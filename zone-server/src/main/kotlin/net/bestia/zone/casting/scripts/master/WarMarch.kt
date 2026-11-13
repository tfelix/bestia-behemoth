package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

/** War March (`skills.yml` id 167): a song for faster feet and swings. */
@Component
class WarMarch : Song(StatusEffectId.SONG_WAR_MARCH)
