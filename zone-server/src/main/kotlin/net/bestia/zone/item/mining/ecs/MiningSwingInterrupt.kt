package net.bestia.zone.item.mining.ecs

import net.bestia.zone.battle.ecs.skill.InterruptibleWork
import org.springframework.stereotype.Component

/** A swing breaks off like digging: by moving, by a hit, by a stun. */
@Component
class MiningSwingInterrupt : InterruptibleWork {
  override val component = MiningSwing::class
}
