package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

/** Mirage Dance (`skills.yml` id 168): a song for more FLEE. */
@Component
class MirageDance : Song(StatusEffectId.SONG_MIRAGE_DANCE)
