package net.bestia.zone.ecs.spawn.townsfolk

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.domain.townsfolk.TownsfolkDomain
import net.bestia.zone.ai.ecs.AiThrottleable
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.bestia.BestiaEntitySpawner
import net.bestia.zone.ecs.battle.status.Invulnerable
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * Puts one household on the ground.
 *
 * A thin wrapper over [BestiaEntitySpawner] rather than a spawner of its own: a townsperson is an ordinary
 * mob with an ordinary AI agent, and everything that makes one different is either a fact in its memory or
 * one of three markers. Duplicating the spawn path to add three markers is how two spawners drift apart.
 *
 * `persistent = false` is the point of the whole layer, for `AmbientSpawnerSystem`'s reason: a town's
 * population is regenerated from its seed whenever somebody comes near, so a database row per villager
 * would be a copy of something already derivable, and one that would go stale the moment the seed's
 * expansion changed.
 */
@Service
class TownsfolkEntitySpawner(
  private val placement: HouseholdPlacement,
  private val bestiaCatalogue: BestiaCatalogue,
  private val spawner: BestiaEntitySpawner,
) {

  /** @return the entities put down, or empty when the settlement has no such household to expand */
  fun spawnHousehold(world: WorldView, settlement: Int, household: Int): List<EntityId> {
    val placed = placement.of(settlement, household) ?: return emptyList()
    val bestiaId = bestiaCatalogue.byIdentifier(COMMONER).id

    return placed.household.members.mapIndexed { member, person ->
      val occupation = placement.occupationFor(placed.household, person)
      val memory = Blackboard().apply {
        set(TownsfolkDomain.OCCUPATION, occupation, Blackboard.PERMANENT)
        placed.workplace?.let { set(TownsfolkDomain.WORK_POSITION, it, Blackboard.PERMANENT) }
      }

      val id = spawner.spawnMob(
        world,
        bestiaId = bestiaId,
        pos = placed.home,
        persistent = false,
        aiMemory = memory,
      )

      val identity = TownsfolkIdentity.of(settlement, household, member)

      // Applied at the end of the tick when this runs inside a system, exactly as `AmbientSpawnerSystem`
      // adds its own markers and for the same reason - `World.tick` holds `iterating` for the scheduler
      // pass. Harmless here: nothing has had a chance to swing at a villager in the tick it was born, and
      // teardown is driven from the residency record rather than from the marker.
      world.modify(id) {
        add(id, Townsfolk(identity))
        add(id, AiThrottleable)
        add(id, Invulnerable)
      }

      LOG.trace { "Spawned ${occupation.id} ${TownsfolkIdentity.describe(identity)} as entity $id" }
      id
    }
  }

  private companion object {
    const val COMMONER = "townsfolk_commoner"
    val LOG = KotlinLogging.logger { }
  }
}
