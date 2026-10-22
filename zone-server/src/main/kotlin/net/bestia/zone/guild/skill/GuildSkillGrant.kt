package net.bestia.zone.guild.skill

import net.bestia.zone.casting.SkillGrant
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.GuildMembership
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** A guild skill is cast at the guild's level, by a master whose rank may use guild skills. */
@Component
class GuildSkillGrant(
  private val tree: GuildSkillTree,
) : SkillGrant {

  override fun grantedLevel(world: World, casterId: EntityId, skillId: Long): Int {
    val node = tree.find(skillId) ?: return 0
    val membership = world.get(casterId, GuildMembership::class)
      ?.takeIf { it.mayUseGuildSkills }
      ?: return 0

    return membership.skillLevel(node.identifier)
  }
}
