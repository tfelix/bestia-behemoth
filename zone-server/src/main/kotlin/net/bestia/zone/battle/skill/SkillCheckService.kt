package net.bestia.zone.battle.skill

import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.skill.SkillId
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

@Service
class SkillCheckService(
  private val world: WorldView
) {

  fun knowsSkill(entityId: EntityId, skillId: Long, minLevel: Int): Boolean {
    val knowsSkill = world.read {
      get(entityId, KnownSkills::class)?.knowsSkill(skillId, minLevel) ?: false
    }

    return knowsSkill
  }

  fun knowsSkill(entityId: EntityId, skillId: SkillId, minLevel: Int): Boolean {
    val knowsSkill = world.read {
      get(entityId, KnownSkills::class)?.knowsSkill(skillId, minLevel) ?: false
    }

    return knowsSkill
  }

  fun knowsAttack(entityId: EntityId, attackId: Long): Boolean {
    val knowsAttack = world.read {
      get(entityId, KnownSkills::class)?.knowsSkill(attackId) ?: false
    }

    return knowsAttack
  }
}