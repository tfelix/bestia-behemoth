package net.bestia.zone.treasure.ecs

import net.bestia.zone.battle.ecs.skill.InterruptibleWork
import org.springframework.stereotype.Component

/** Digging breaks off like a craft: by moving, by a hit, by a stun. */
@Component
class DiggingInterrupt : InterruptibleWork {
  override val component = Digging::class
}
