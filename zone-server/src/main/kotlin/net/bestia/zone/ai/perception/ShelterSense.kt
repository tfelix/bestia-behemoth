package net.bestia.zone.ai.perception

import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain
import net.bestia.zone.ecs.AoiLayer
import net.bestia.zone.ecs.EntityAOIService
import net.bestia.zone.ecs.battle.damage.TakenDamage
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.spawn.townsfolk.Townsfolk
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Notices fighting in the street, and which doorway to make for.
 *
 * ### Violence, not a stranger
 *
 * The obvious trigger is `CommonKeys.ENEMY_IN_SIGHT`, and it is the wrong one. Perception counts anything
 * with a `Master` as hostile — that is a *player* — so a town wired to it would empty the moment anybody
 * walked in. What frightens a villager is somebody being hit, so that is what is looked for: an entity
 * nearby whose [TakenDamage] ledger has an entry inside [ALARM_MEMORY_MS]. It costs nothing new to
 * maintain, and it fires for a player fighting a boar and for a boar mauling a player alike.
 *
 * Townsfolk only, rejected in one `has`. Creatures have their own answer to a fight and it is not a door.
 */
@Component
class ShelterSense(
  private val aoi: EntityAOIService,
  private val doors: ShelterDoors,
) : Sense {

  override val name = "shelter"

  /**
   * Faster than foraging and slower than sight. A fight is over in seconds, so a slower sweep would have
   * people stroll out of doorways the brawl has not finished with.
   */
  override val intervalSeconds = 1f

  override val reads: ComponentClassSet = setOf(TakenDamage::class, Townsfolk::class)

  override fun sense(context: SenseContext) {
    if (!context.world.has(context.entityId, Townsfolk::class)) return

    val threat = nearestViolence(context.world, context.entityId, context.position)
    if (threat == null) {
      context.forget(TownsfolkDomain.THREAT_POSITION)
      context.forget(TownsfolkDomain.SHELTER_DOOR)
      return
    }

    context.remember(TownsfolkDomain.THREAT_POSITION, threat)

    // The door you set off for is the door you reach. Re-picking every sweep would have somebody turn
    // round mid-street each time the fight moved, which reads as dithering rather than as fear.
    if (context.recall(TownsfolkDomain.SHELTER_DOOR) != null) return

    doorAwayFrom(context.position, threat)?.let { context.remember(TownsfolkDomain.SHELTER_DOOR, it) }
  }

  /** Where the nearest recently-struck thing is standing, ignoring ourselves. */
  private fun nearestViolence(world: World, self: EntityId, at: Vec3L): Vec3L? {
    return aoi.queryEntitiesInCube(at, ALARM_RADIUS * 2, AoiLayer.DYNAMIC_ONLY)
      .asSequence()
      .filter { it != self }
      .filter { world.get(it, TakenDamage::class)?.mostRecentAttacker(ALARM_MEMORY_MS) != null }
      .mapNotNull { world.get(it, Position::class)?.toVec3L() }
      .filter { it.distance(at) <= ALARM_RADIUS }
      .minByOrNull { it.distance(at) }
  }

  /**
   * The nearest door that is closer to us than it is to the fight.
   *
   * Nearest alone sends people straight through the brawl often enough to look silly, and the far side
   * of the street is usually only a few paces further. Where the fight is nearer to every door than we
   * are, plain nearest is all that is left - and is still better than standing in the open.
   */
  private fun doorAwayFrom(at: Vec3L, threat: Vec3L): Vec3L? {
    val candidates = doors.near(at, DOOR_REACH)
    val ourSide = candidates.filter { at.distance(it) <= threat.distance(it) }

    return ourSide.ifEmpty { candidates }.minByOrNull { it.distance(at) }
  }

  companion object {
    /** How far off a fight has to be before it is somebody else's business. In tiles. */
    const val ALARM_RADIUS = 14L

    /**
     * How long after a blow the fight still counts as going on.
     *
     * Long enough that the pause between two swings does not read as peace, short enough that a street
     * comes back to life a few seconds after the last one. `TakenDamage`'s own five-minute retention is
     * for attributing loot and is far too long to hold a town indoors.
     */
    const val ALARM_MEMORY_MS = 8_000L

    /**
     * How far somebody will run for a door, in tiles.
     *
     * A village is comfortably inside it and a farmer alone in a distant field is not, which is the right
     * answer for him: there is no door in sight, so he keeps working.
     */
    const val DOOR_REACH = 80L
  }
}
