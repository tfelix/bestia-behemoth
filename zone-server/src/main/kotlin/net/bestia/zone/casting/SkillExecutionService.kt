package net.bestia.zone.casting

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.skill.Skill
import net.bestia.zone.skill.SkillRepository
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves an activated skill: builds the context, runs the script, applies whatever number came back.
 * The single place a skill takes effect, whether it fired instantly or at the end of a channelled cast.
 *
 * ### Skills resolve on the tick thread, between ticks
 *
 * [execute] posts the resolution to the tick thread and returns. That is what lets a script query and spawn
 * and do relational work itself, instead of returning a spec for this service to enact. It runs between
 * ticks, where `World.iterating` is false, so structural changes apply immediately and a get-or-create on
 * the target's `Damage` component is atomic against every other caster. Casts resolve in the order they
 * were posted. [SkillBudget] bounds how long one cast may hold the tick.
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
) {

  /** Skills never change after the import, and resolution runs on the tick, so they are read once at boot. */
  private val skillCache = ConcurrentHashMap<Long, Skill>()

  /** Loads the whole skill catalogue; called at boot, after the importer. */
  fun warmUp() {
    skillRepository.findAll().forEach { skillCache[it.id] = it }
  }

  /** The catalogue row for [skillId], from memory, or null for an id the catalogue does not know. */
  fun skillOf(skillId: Long): Skill? {
    return skillCache[skillId]
  }

  /** Queues [skillId] for resolution on the tick thread and returns immediately, from any thread. */
  fun execute(
    world: World,
    casterId: EntityId,
    skillId: Long,
    skillLevel: Int,
    targetEntityId: EntityId?,
    targetPosition: Vec3L?
  ) {
    world.post {
      resolve(world, casterId, skillId, skillLevel, targetEntityId, targetPosition)
    }
  }

  private fun resolve(
    world: World,
    casterId: EntityId,
    skillId: Long,
    skillLevel: Int,
    targetEntityId: EntityId?,
    targetPosition: Vec3L?
  ) {
    val skill = skillOf(skillId)
    if (skill == null) {
      LOG.warn { "Skill $skillId activated by $casterId is not in the catalogue, ignoring" }
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
