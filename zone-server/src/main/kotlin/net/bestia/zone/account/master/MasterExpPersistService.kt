package net.bestia.zone.account.master

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.account.Master as MasterComponent
import net.bestia.zone.ecs.battle.exp.Exp
import net.bestia.zone.ecs.battle.level.Level
import net.bestia.zone.ecs.battle.status.SkillPoints
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * A single entry point for persisting highly valuable
 */
@Service
class MasterExpPersistService(
  private val asyncJobExecutor: AsyncJobExecutor,
  private val masterExpWriter: MasterExpWriter,
) {

  fun schedulePersistExperience(world: World, masterEntityId: EntityId) {
    val masterId = world.get(masterEntityId, MasterComponent::class)?.masterId
    if (masterId == null) {
      LOG.warn { "Entity $masterEntityId has no Master component, cannot persist its level/exp" }
      return
    }

    asyncJobExecutor.submit(masterId) {
      val level = world.get(masterEntityId, Level::class)?.level
      val exp = world.get(masterEntityId, Exp::class)?.value
      if (level == null || exp == null) {
        LOG.warn { "Entity $masterEntityId is missing Level/Exp, cannot persist its level/exp" }
        return@submit
      }

      // Optional: a master always has SkillPoints, but if it is somehow missing we still persist
      // level/exp rather than clobbering the stored skill points with a bogus value.
      val skillPoints = world.get(masterEntityId, SkillPoints::class)?.value

      masterExpWriter.persist(masterId, level, exp, skillPoints)
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

/**
 * Transactional write half of [MasterExpPersistService] - split into its own bean so the
 * `@Transactional` on [persist] goes through Spring's proxy instead of being called via
 * self-invocation (see [MasterExpPersistService]'s doc for why that distinction matters here).
 */
@Service
class MasterExpWriter(
  private val masterRepository: MasterRepository,
) {

  @Transactional
  fun persist(masterId: Long, level: Int, exp: Int, skillPoints: Int?) {
    val master = masterRepository.findByIdForUpdate(masterId)
    if (master == null) {
      LOG.warn { "Master $masterId was not found, cannot persist its level/exp" }
      return
    }

    master.level = level
    master.exp = exp
    if (skillPoints != null) {
      master.skillPoints = skillPoints
    }
    masterRepository.save(master)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
