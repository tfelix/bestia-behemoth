package net.bestia.zone.casting

import net.bestia.zone.battle.ecs.effects.AreaEffect
import net.bestia.zone.battle.potency.Potency
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import org.springframework.stereotype.Component
import kotlin.random.Random

/**
 * Whether a ground spell may take root at a spot: a zone that blocks ground spells, like Land Protector, refuses
 * it unless the spell's Potency wins the contest, rolled once per spell (spell potency page, "Counter-Magic").
 */
@Component
class GroundWards(private val random: Random = Random.Default) {

  fun blocks(world: World, at: Vec3L, potency: Int): Boolean {
    var blocked = false
    world.query(AreaEffect::class, Position::class).each { _ ->
      val zone = get<AreaEffect>()
      if (blocked || !zone.mechanics.blocksGroundSpells) return@each

      val centre = get<Position>().toVec3L()
      if (!covers(centre, zone.radiusTiles, at)) return@each

      blocked = random.nextDouble() < Potency.edge(zone.potency, potency)
    }

    return blocked
  }

  /** The zone's cube, like the area's own victims: a radius of 1 covers the 3x3 around its centre. */
  private fun covers(centre: Vec3L, radius: Long, at: Vec3L): Boolean {
    return kotlin.math.abs(at.x - centre.x) <= radius && kotlin.math.abs(at.y - centre.y) <= radius
  }
}
