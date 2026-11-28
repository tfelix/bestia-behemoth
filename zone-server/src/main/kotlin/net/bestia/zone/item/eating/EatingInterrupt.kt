package net.bestia.zone.item.eating

import net.bestia.zone.battle.ecs.skill.InterruptibleWork
import org.springframework.stereotype.Component

/** A meal is broken off like a craft: by moving, by a hit, by a stun. The meal stays in the bag. */
@Component
class EatingInterrupt : InterruptibleWork {
  override val component = Eating::class
}
