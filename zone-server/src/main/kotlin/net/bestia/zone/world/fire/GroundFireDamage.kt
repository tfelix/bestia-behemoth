package net.bestia.zone.world.fire

import net.bestia.zone.ecs.battle.effects.AreaEffect
import net.bestia.zone.ecs.battle.effects.AreaEffectSpawner
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.stream.ChunkService
import org.springframework.stereotype.Component

/**
 * Keeps an invisible [AreaEffect] over a fire, so standing in one hurts.
 *
 * ### Why the existing effect engine rather than a damage pass of its own
 *
 * `AreaEffectSystem` already does everything needed: its own accumulator at any cadence, a `while` loop so a
 * long frame delivers every swallowed tick, the get-or-create on `Damage` inside `world.defer`, the
 * `DamageEntitySMSG` broadcast, `hitsCaster`, and `AoiLayer.ALL` so props are in scope. Its KDoc says the
 * generic case out loud - *"a 1.2s fire patch and a 3s poison cloud are the same code with different
 * numbers"* - and a grass fire is one more set of numbers.
 *
 * ### Invisible, growing, and never moving
 *
 * No `EntityVisual`: the fire's appearance is the burning mask the client already receives, so an effect with a
 * visual would draw a second fire on top of the real one.
 *
 * The effect is **resized** as the fire grows rather than re-spawned, because re-spawning would re-broadcast an
 * entity appearing every second - and it is deliberately never *moved*. A cube grown around the ignition point
 * covers the whole fire just as well as one recentred on it, since the bounding box only ever expands; and not
 * writing an existing entity's `Position` is what lets `GroundFireSystem` declare `AreaEffect` alone instead of
 * conflicting with half the engine over the position store.
 *
 * ### The cube, and what it costs
 *
 * An `AreaEffect` is an axis-aligned cube with no distance filter - `Ember`'s own KDoc admits the same thing:
 * *"they are still a circle and a square"*. So a cube over a fire's bounding box also catches somebody
 * standing on unburnt ground inside it, which for a ring or crescent front is ground that is genuinely not on
 * fire. That is the accepted price of reusing the engine; the escalation, if it reads badly, is a mask-filtered
 * pass here, which would then be ~40 lines duplicating `applyTick` for one honest reason - the shape of the
 * region.
 */
@Component
class GroundFireDamage(
  private val config: GroundFireConfig,
  private val spawner: AreaEffectSpawner,
  private val chunkService: ChunkService,
) {

  /** Puts an effect over [fire], or moves and resizes the one already there. */
  fun apply(world: World, fire: GroundFire) {
    if (fire.isOut) {
      fire.effectId?.let { world.destroy(it) }
      fire.effectId = null
      return
    }

    val origin = fire.originX to fire.originY
    val centre = Vec3L(
      origin.first,
      origin.second,
      // A fire is on the ground and the cube is cubic in all three axes, so sitting at the surface is what
      // keeps a player standing in it inside the box.
      groundZ(origin.first, origin.second)
    )

    // Far enough from the ignition point to cover every burning cell. `AreaEffect.radiusTiles` is a half-edge
    // and `AreaEffectSystem` doubles it back - the one place this conversion happens, and the trap
    // `InterestRange` also documents.
    val radius = fire.radiusFromOrigin

    val existing = fire.effectId
    if (existing != null && world.isAlive(existing)) {
      world.get(existing, AreaEffect::class)?.let { effect ->
        effect.radiusTiles = radius
        // Kept alive by topping the counter up rather than by a long duration, so a fire that goes out stops
        // hurting on its own within one interval even if nothing tidies up.
        effect.remainingTicks = REFRESH_TICKS
      }
      return
    }

    fire.effectId = spawner.spawn(
      world = world,
      center = centre,
      visualId = null,
      effect = AreaEffect(
        casterId = fire.casterId,
        skillId = fire.skillId,
        skillLevel = fire.skillLevel,
        radiusTiles = radius,
        damagePerTick = config.damagePerTick,
        tickIntervalSeconds = config.damageIntervalSeconds,
        remainingTicks = REFRESH_TICKS,
        // Fire on the ground burns whoever lit it, which is `AreaEffect.hitsCaster`'s own example.
        hitsCaster = true
      )
    )
  }

  private fun groundZ(voxelX: Long, voxelY: Long): Long =
    chunkService.surfaceElevationAt(voxelX, voxelY)?.toLong() ?: 0L

  private companion object {
    /**
     * Ticks the effect is topped back up to on every refresh.
     *
     * Small on purpose: the effect outliving its fire by a couple of ticks is harmless, and outliving it by a
     * minute would be a patch of invisible damage nobody can see the cause of.
     */
    const val REFRESH_TICKS = 3
  }
}
