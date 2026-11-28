package net.bestia.zone.faction.banishment

import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.honor.HonorStandingSystem
import net.bestia.zone.identity.ecs.FactionMembership
import org.springframework.stereotype.Component

/** Puts the unsworn mark back on a master who logs in, since the mark itself is never stored. */
@Component
class UnswornSystem(
  private val mark: UnswornMark,
) : System {
  override val phase = Phase.UPKEEP
  override val after = setOf(HonorStandingSystem::class)
  override val schedule: Schedule = Schedule.EverySeconds(10f)

  override val reads: ComponentClassSet = setOf(FactionMembership::class)
  override val writes: ComponentClassSet = setOf(StatusEffects::class, IsStatusValueDirty::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(FactionMembership::class).each { master -> mark.renew(world, master) }
  }
}
