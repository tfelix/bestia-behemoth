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
 * The single durable entry point for [Master.level]/[Master.exp]/[Master.skillPoints].
 * [schedulePersistExperience] resolves [masterEntityId]'s DB id up front (cheap, and stable for as
 * long as the entity is online, so there is nothing to go stale) and uses it as the
 * [AsyncJobExecutor] key, so writes for the same master never run concurrently with each other on
 * this JVM. The [Level]/[Exp]/[SkillPoints] values themselves are deliberately re-read from [world]
 * only once the job actually runs, not here: a queued job may sit behind others for the same
 * master, and by the time it is its turn further gains could already have landed on the entity -
 * reading eagerly would risk persisting a stale snapshot and clobbering a newer one.
 *
 * Skill points are persisted alongside level/exp because a level-up grants one (see
 * `GainExpSystem`); persisting them here makes that gain durable immediately rather than only at
 * the next full entity snapshot. The live [SkillPoints] component is the source of truth - spends
 * (`MasterSkillTreeService.investSkillPoints`) keep it in sync too - so writing its current value
 * is always correct.
 *
 * The actual write ([MasterExpWriter.persist]) lives on a separate bean rather than a private
 * method here so `@Transactional` actually applies: Spring's transactional advice is proxy-based
 * and does not intercept self-invocation (a method calling another method on `this`), so a
 * `@Transactional` method called from within the same class would silently run with no
 * transaction. That would matter a lot here: `SELECT ... FOR UPDATE`
 * ([MasterRepository.findByIdForUpdate]) only guards against a concurrent read-modify-write if the
 * lock is held for the whole find-mutate-save sequence - without a real transaction wrapping all
 * three, the lock would already be released again before the mutated row is saved.
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
