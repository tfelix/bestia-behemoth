package net.bestia.zone.item.mining

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.skill.OwnerSkills
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.persistence.SkillRepository
import net.bestia.zone.skill.persistence.findByIdentifier
import net.bestia.zone.util.EntityId
import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import org.springframework.stereotype.Component
import java.util.Random

/**
 * Rich Veins: a chance per mined iron or gem block for one more piece. It reaches the master's bestias at their
 * shaft, like any owner-reach passive.
 */
@Component
class RichVeins(
  private val skills: SkillRepository,
  private val random: Random = ThreadLocalRandomSource,
) {

  val reads: ComponentClassSet = OwnerSkills.READS

  private val richVeinsId: Long? by lazy { skills.findByIdentifier(SkillId.M_RICH_VEINS)?.id }

  /** Resolves the skill id at boot, so the tick never reaches the database for it. */
  fun warmUp() {
    richVeinsId
  }

  /** Extra pieces for one broken voxel of [stack], mined by [minerId]. */
  fun extraPieces(world: World, minerId: EntityId, stack: OreYield.Stack): Int {
    // Gold is the coin supply, quoted per voxel; a second piece would mint money the world was not sized for.
    if (stack.isCoinMetal) return 0
    val level = richVeinsId?.let { OwnerSkills.levelOf(world, minerId, it) } ?: return 0
    val chance = CHANCE_PER_LEVEL * level.coerceIn(0, MAX_LEVEL)

    return if (random.nextDouble() < chance) 1 else 0
  }

  private companion object {
    const val MAX_LEVEL = 5
    const val CHANCE_PER_LEVEL = 0.1
  }
}
