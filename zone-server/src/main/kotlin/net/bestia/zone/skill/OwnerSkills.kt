package net.bestia.zone.skill

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.util.EntityId

/**
 * The level of a master skill that works for a master and for the master's bestias alike - Weather Resistance,
 * Expert Taming. A master reads its own; a bestia reads its master's, whether or not the master stands near.
 */
object OwnerSkills {

  val READS: ComponentClassSet = setOf(KnownSkills::class, Master::class, OwnedBestia::class)

  fun levelOf(world: World, entityId: EntityId, skillId: Long): Int {
    if (world.has(entityId, Master::class)) {
      return world.get(entityId, KnownSkills::class)?.levelOf(skillId) ?: 0
    }
    val masterId = world.get(entityId, OwnedBestia::class)?.masterId ?: return 0

    return masterSkillsOf(world, masterId)?.levelOf(skillId) ?: 0
  }

  /** A scan of the masters in the world, few enough that a lookup table would only be a second truth. */
  private fun masterSkillsOf(world: World, masterId: Long): KnownSkills? {
    var found: KnownSkills? = null
    world.query(Master::class, KnownSkills::class).each { _ -> if (get<Master>().masterId == masterId) found = get<KnownSkills>() }

    return found
  }
}
