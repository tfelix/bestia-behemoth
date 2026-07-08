package net.bestia.zone.ecs.battle

import net.bestia.zone.component.LearnedSkillsSMSG
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.ecs.SyncContext
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.EntityId
import net.bestia.zone.ecs.player.Account
import net.bestia.zone.message.entity.EntitySMSG

/**
 * The custom/individually-learned skills of an entity - attackId -> current level. This is the
 * single shared shape used both for a captured bestia's item-taught custom skills and for a
 * bestia master's skill-tree investments. Deliberately does NOT include an entity's fixed
 * level-gated attacks (species table for bestias, nothing equivalent for masters) - the client
 * already derives those locally from level, and merges them with what this component syncs.
 *
 * See [AvailableAttacks] for the full (fixed + custom) server-side validation view.
 */
class LearnedSkills(
  private val skillLevels: MutableMap<Long, Int> = mutableMapOf()
) : Component, Dirtyable {

  private var dirty = true

  val availableSkills: Map<Long, Int> get() = skillLevels

  fun learnOrUpdate(attackId: Long, level: Int) {
    if (skillLevels[attackId] != level) {
      skillLevels[attackId] = level
      dirty = true
    }
  }

  override fun isDirty(): Boolean = dirty

  override fun clearDirty() {
    dirty = false
  }

  override fun toEntityMessage(entityId: Long): EntitySMSG {
    return LearnedSkillsSMSG(
      entityId = entityId,
      skills = skillLevels.map { (attackId, level) -> LearnedSkillsSMSG.SkillEntry(attackId, level) }
    )
  }

  override fun syncTargets(context: SyncContext, entityId: EntityId): SyncTargets {
    val owner = context.world.get(entityId, Account::class)?.accountId
    return SyncTargets.Accounts(setOfNotNull(owner))
  }
}
