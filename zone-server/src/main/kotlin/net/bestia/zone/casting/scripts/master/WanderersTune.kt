package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.status.StatusEffectId
import org.springframework.stereotype.Component

/** Wanderer's Tune (`skills.yml` id 169): a long song for less stamina drain. */
@Component
class WanderersTune : Song(StatusEffectId.SONG_WANDERERS_TUNE)
