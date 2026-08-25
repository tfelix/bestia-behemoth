package net.bestia.zone.world.fire

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.battle.effects.AreaEffect
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Steps every burning fire once a tick.
 *
 * ### `@Order(46)`
 *
 * Before `GroundOverlaySystem` (47), which is what matters: a fire marks columns dirty as it spreads and the
 * overlay flushes them after, so the mask a client receives is the one this tick produced rather than last
 * tick's. Also before `AreaEffectSystem` (48), so an effect this resizes is ticked at its new size in the same
 * tick, and before `ReceivedDamageSystem` (50), which drains the `Damage` that effect stages.
 *
 * ### It declares `AreaEffect` and nothing else, deliberately
 *
 * The fire holds its own state, so the only component it touches on an entity that already existed is the
 * `AreaEffect` it resizes - which conflicts with `AreaEffectSystem` and gets the ordering above enforced
 * rather than merely requested.
 *
 * It does **not** declare what promotion writes. `PropPromotionService` is called from inside
 * `AreaEffectSystem`, which declares them itself; and the effect entity this *creates* needs nothing declared
 * for its own `Position`, on `DeathSystem.spawnLoot`'s precedent - a freshly created id "was not there for any
 * other system to conflict on".
 *
 * Declaring the wider set was tried and is a trap: an always-present system conflicting with `Position` and
 * `Health` cannot share a wave with most of the engine, which flattens the scheduling for everything.
 */
@Component
@Order(46)
class GroundFireSystem(
  private val fire: GroundFireService,
) : System {

  override val schedule: Schedule = Schedule.EveryTick

  override val reads: ComponentClassSet = emptySet()

  override val writes: ComponentClassSet = setOf(AreaEffect::class)

  override fun update(world: World, deltaTime: Float) {
    if (fire.isIdle) return
    fire.step(world, deltaTime)
  }
}
