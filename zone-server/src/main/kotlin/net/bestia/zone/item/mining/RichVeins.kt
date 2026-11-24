package net.bestia.zone.item.mining

import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.gear.ToolTerm
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.skill.OwnerSkills
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.SkillIds
import net.bestia.zone.util.EntityId
import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import org.springframework.stereotype.Component
import java.util.Random

/**
 * Rich Veins: a chance per mined iron or gem block for one more piece. It reaches the master's bestias at their
 * shaft, like any owner-reach passive. A Rich Seam essence in the pickaxe adds to the chance.
 */
@Component
class RichVeins(
  private val skillIds: SkillIds,
  private val random: Random = ThreadLocalRandomSource,
) {

  val reads: ComponentClassSet = OwnerSkills.READS + CombatStats::class

  private val richVeinsId: Long? get() = skillIds[SkillId.M_RICH_VEINS]

  /** Extra pieces for one broken voxel of [stack], mined by [minerId]. */
  fun extraPieces(world: World, minerId: EntityId, stack: OreYield.Stack): Int {
    // Gold is the coin supply, quoted per voxel; a second piece would mint money the world was not sized for.
    if (stack.isCoinMetal) return 0
    val level = richVeinsId?.let { OwnerSkills.levelOf(world, minerId, it) } ?: 0
    val essencePercent = world.get(minerId, CombatStats::class)?.values?.toolPercent(ToolTerm.SECOND_ORE_CHANCE) ?: 0
    val chance = CHANCE_PER_LEVEL * level.coerceIn(0, MAX_LEVEL) + essencePercent / 100.0

    return if (random.nextDouble() < chance) 1 else 0
  }

  private companion object {
    const val MAX_LEVEL = 5
    const val CHANCE_PER_LEVEL = 0.1
  }
}
