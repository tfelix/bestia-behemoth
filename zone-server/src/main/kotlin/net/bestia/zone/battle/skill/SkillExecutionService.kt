package net.bestia.zone.battle.skill

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.skill.Skill
import net.bestia.zone.skill.SkillRepository
import net.bestia.zone.skill.findByIdOrThrow
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves an activated skill: builds the context, runs the script, applies whatever number came back.
 * The single place a skill takes effect, whether it fired instantly or at the end of a channelled cast.
 *
 * ### Skills resolve off the tick thread
 *
 * [execute] enqueues and returns; the work happens on an [AsyncJobExecutor] worker. That is what lets a
 * script query and spawn and do relational work itself, instead of returning a spec for this service to
 * enact - the arrangement the deleted `AreaEffectResult`/`CraftingResult`/`SurveyResult` types existed to
 * work around.
 *
 * It is safe because `World.tick` holds the world lock for its whole duration, and every scope a script
 * opens takes that same lock. A cast therefore never interleaves with a tick: inside one of its scopes
 * `World.iterating` is always false, so structural changes apply immediately rather than being deferred,
 * and a get-or-create on the target's `Damage` component is atomic against every other caster. The cost
 * is lock *contention*, which [SkillBudget] bounds.
 *
 * Jobs are keyed on the caster, so two casts by the same entity resolve in the order they were activated.
 *
 * ### What is still checked here rather than in the script
 *
 * Mana and liveness, because every skill pays them the same way. Everything else - who may be targeted,
 * what the skill does, what it leaves behind - is the script's.
 */
@Service
class SkillExecutionService(
  private val skillRepository: SkillRepository,
  private val skillStrategyFactory: SkillStrategyFactory,
  private val skillContextFactory: SkillContextFactory,
  private val asyncJobExecutor: AsyncJobExecutor,
) {

  /**
   * Skills are immutable once imported, so they are cached rather than hitting JPA on every cast. Kept
   * even though resolution moved off the tick thread: a cast is on the critical path of somebody pressing
   * a button, and a round trip per keypress is still a round trip.
   */
  private val skillCache = ConcurrentHashMap<Long, Skill>()

  /**
   * Queues [skillId] for resolution and returns immediately, from any thread.
   *
   * The worker takes the world lock, so it waits out whatever scope the caller is inside. From a system that
   * is expected and bounded - `CastingSystem` runs inside `World.tick`, which holds the lock for the whole
   * tick, and the worker simply starts when the tick ends. From a message handler it is worth avoiding, since
   * a handler's scope is as long as the handler makes it; `ActivateSkillHandler` calls this outside its
   * `modify` block for that reason.
   *
   * A waiting worker is one of four in a pool shared with `ZoneEngine`'s outbound broadcasts, so a cast
   * queued mid-tick does cost the pool a worker for the rest of that tick. It cannot deadlock: nothing ever
   * waits on a submitted job.
   */
  fun execute(
    world: WorldView,
    casterId: EntityId,
    skillId: Long,
    skillLevel: Int,
    targetEntityId: EntityId?,
    targetPosition: Vec3L?
  ) {
    asyncJobExecutor.submit(casterId) {
      resolve(world, casterId, skillId, skillLevel, targetEntityId, targetPosition)
    }
  }

  private fun resolve(
    world: WorldView,
    casterId: EntityId,
    skillId: Long,
    skillLevel: Int,
    targetEntityId: EntityId?,
    targetPosition: Vec3L?
  ) {
    val skill = try {
      skillCache.computeIfAbsent(skillId) { skillRepository.findByIdOrThrow(it) }
    } catch (e: Exception) {
      LOG.warn(e) { "Skill $skillId activated by $casterId is not in the catalogue, ignoring" }
      return
    }

    val strategy = try {
      skillStrategyFactory.getSkillStrategy(skill)
    } catch (e: Exception) {
      LOG.warn(e) { "No usable strategy for skill $skillId (script=${skill.script}), ignoring activation" }
      return
    }

    val ctx = skillContextFactory.create(world, casterId, skill, skillLevel, targetEntityId, targetPosition)
    if (ctx == null) {
      LOG.debug { "Skill $skillId by $casterId fizzled: caster or target no longer resolvable" }
      return
    }

    try {
      // Checked here rather than at activation on purpose: for a channelled skill the caster may have
      // drifted out of range or lost line of sight while casting, which must make the skill fizzle.
      if (!strategy.isCastPossible(ctx)) {
        LOG.debug { "Skill $skillId by $casterId fizzled: the cast is no longer possible" }
        return
      }

      // After the possibility check and before the effect, so a refused cast costs nothing and a
      // resolved one is always paid for.
      if (!ctx.world.consumeCasterMana(skill.manaCost)) {
        LOG.debug { "Skill $skillId by $casterId fizzled: not enough mana" }
        return
      }

      val result = strategy.execute(ctx)

      // A skill whose whole effect is a patch of ground, a station or a chart has no number to show, and
      // a ground-targeted one has no entity to show it on.
      if (result != null && targetEntityId != null) {
        ctx.world.apply(targetEntityId, result)
      }

      LOG.trace { "Skill $skillId by $casterId resolved, spending ${skill.manaCost} mana" }
    } catch (e: SkillBudgetExceededException) {
      // Not rolled back: the ECS has no transaction, so whatever the script already did stands. A script
      // that trips this has a bug, and the log is what surfaces it.
      LOG.error(e) { "Skill $skillId (script=${skill.script}) by $casterId overran its execution budget" }
    } catch (e: Exception) {
      LOG.error(e) { "Skill $skillId (script=${skill.script}) by $casterId failed" }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
