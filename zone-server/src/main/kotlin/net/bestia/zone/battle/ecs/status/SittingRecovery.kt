package net.bestia.zone.battle.ecs.status

import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Animation
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.skill.BasicSkillGate
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.tree.MasterSkillTreeRegistry
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Basic Skill 4: a master who sits down recovers HP and mana twice as fast, and so do the master's bestias when they
 * sit. Asked by the regeneration systems on each beat, since sitting comes and goes far more often than a recalc.
 */
@Component
class SittingRecovery(
  private val skillTree: MasterSkillTreeRegistry,
) {

  /** How much faster [entityId] regenerates for sitting: 2 when sitting under a master of rank 4, else 1. */
  fun factorOf(world: World, entityId: EntityId): Int {
    if (world.get(entityId, Animation::class)?.currentAnimation != Animation.AnimationKind.SIT) return 1

    return if (basicSkillRankOf(world, entityId) >= BasicSkillGate.SITTING_RECOVERY_RANK) SITTING_FACTOR else 1
  }

  private fun basicSkillRankOf(world: World, entityId: EntityId): Int {
    val basicSkillId = skillTree.basicSkillId ?: return 0
    val known = world.get(entityId, KnownSkills::class)?.takeIf { world.has(entityId, Master::class) }
      ?: masterSkillsOf(world, world.get(entityId, OwnedBestia::class)?.masterId ?: return 0)
      ?: return 0

    return known.levelOf(basicSkillId)
  }

  private fun masterSkillsOf(world: World, masterId: Long): KnownSkills? {
    var found: KnownSkills? = null
    world.query(Master::class, KnownSkills::class).each { _ -> if (get<Master>().masterId == masterId) found = get<KnownSkills>() }

    return found
  }

  private companion object {
    const val SITTING_FACTOR = 2
  }
}
