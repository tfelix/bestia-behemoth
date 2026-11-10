package net.bestia.zone.crafting.station

import net.bestia.zone.account.persistence.LearnedSkillRepository
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.crafting.Crafters
import net.bestia.zone.crafting.KnownRecipeStore
import net.bestia.zone.crafting.ecs.KnownRecipes
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.util.EntityId
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * What a master knows, for their bestias to craft with while the master is logged out.
 *
 * Kept from the master's own entity while they play, and loaded from the database for a master who has not
 * played since the last restart. Crafting skills and blueprints only change while their master plays, so the
 * copy kept at logout stays true.
 */
@Component
class CrafterProfiles(
  private val crafters: Crafters,
  private val learnedSkills: LearnedSkillRepository,
  private val knownRecipes: KnownRecipeStore,
  private val masters: MasterRepository,
  private val asyncJobExecutor: AsyncJobExecutor,
) {

  private class Knowledge(val master: Master, val skills: KnownSkills, val blueprints: KnownRecipes)

  private val byMaster = ConcurrentHashMap<Long, Knowledge>()
  private val loading = ConcurrentHashMap.newKeySet<Long>()

  fun remember(world: World, masterEntityId: EntityId) {
    val master = world.get(masterEntityId, Master::class) ?: return
    val skills = world.get(masterEntityId, KnownSkills::class) ?: return
    val blueprints = world.get(masterEntityId, KnownRecipes::class) ?: return

    byMaster[master.masterId] = Knowledge(master, skills, blueprints)
  }

  /** The crafter [workerId] is for its absent master, or null while what the master knows is still loading. */
  fun crafterFor(world: World, workerId: EntityId, masterId: Long): Crafters.Crafter? {
    val knowledge = byMaster[masterId]
    if (knowledge == null) {
      load(masterId)
      return null
    }

    return crafters.ofAbsentMaster(world, workerId, knowledge.master, knowledge.skills, knowledge.blueprints)
  }

  private fun load(masterId: Long) {
    if (!loading.add(masterId)) return

    asyncJobExecutor.submit(masterId) {
      try {
        val name = masters.findByIdOrNull(masterId)?.name ?: return@submit
        val skills = learnedSkills.findAllByMasterId(masterId).associate { it.skill.id to it.level }
        val blueprints = knownRecipes.load(masterId)

        byMaster.putIfAbsent(
          masterId,
          Knowledge(Master(masterId, name), KnownSkills(skills.toMutableMap()), KnownRecipes(blueprints.toMutableSet()))
        )
      } finally {
        loading.remove(masterId)
      }
    }
  }
}
