package net.bestia.zone.economy.shop

import net.bestia.zone.ecs.core.World
import net.bestia.zone.skill.OwnerSkills
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.persistence.SkillRepository
import net.bestia.zone.skill.persistence.findByIdentifier
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Haggling: a Trader pays 2% less and is paid 2% more per level at a merchant. */
@Component
class Haggling(private val skills: SkillRepository) {

  private val hagglingId: Long? by lazy { skills.findByIdentifier(SkillId.M_HAGGLING)?.id }

  fun shareOf(world: World, traderId: EntityId): Double {
    val level = hagglingId?.let { OwnerSkills.levelOf(world, traderId, it) } ?: 0

    return level * SHARE_PER_LEVEL
  }

  private companion object {
    const val SHARE_PER_LEVEL = 0.02
  }
}
