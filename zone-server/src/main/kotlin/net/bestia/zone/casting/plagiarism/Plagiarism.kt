package net.bestia.zone.casting.plagiarism

import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.casting.SkillGrant
import net.bestia.zone.ecs.core.World
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.persistence.SkillRepository
import net.bestia.zone.skill.persistence.findByIdentifier
import net.bestia.zone.skill.tree.MasterSkillTreeRegistry
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import java.util.Random

/**
 * Plagiarism (`skills.yml` id 126): a spell that strikes the master may be copied, `10%/lv` of the time, to be cast
 * once as a [PlagiarizedSkill]. The copy is known to the cast checks through [grantedLevel]. Below Lv. 5 a new copy
 * replaces the old one; at Lv. 5 the old one is kept until it is cast.
 *
 * The attack level copied is capped at `20/lv`, and not at Lv. 5. A bestia attack's attack level is the lowest level
 * a species learns it at; a master spell counts 10 per skill level and is copied at the cap when it is above it.
 */
@Component
class Plagiarism(
  private val skills: SkillRepository,
  private val masterTree: MasterSkillTreeRegistry,
  private val bestias: BestiaCatalogue,
  private val skillLists: SkillListPush? = null,
  private val random: Random = ThreadLocalRandomSource,
) : SkillGrant {

  private val plagiarismId: Long? by lazy { skills.findByIdentifier(SkillId.M_PLAGIARISM)?.id }

  fun warmUp() {
    plagiarismId
  }

  override fun grantedLevel(world: World, casterId: EntityId, skillId: Long): Int {
    val copy = world.get(casterId, PlagiarizedSkill::class) ?: return 0

    return if (copy.skillId == skillId) copy.level else 0
  }

  /** [casterId]'s spell [skillId] at [skillLevel] struck [struckId], which may copy it. */
  fun onStruck(world: World, struckId: EntityId, casterId: EntityId, skillId: Long, skillLevel: Int) {
    if (struckId == casterId) return
    val level = plagiarismId?.let { world.get(struckId, KnownSkills::class)?.levelOf(it) } ?: 0
    if (level <= 0) return
    if (level >= KEEPS_COPY_LEVEL && world.has(struckId, PlagiarizedSkill::class)) return

    val copiedLevel = copiedLevel(skillId, skillLevel, level) ?: return
    if (random.nextInt(100) >= chancePercent(level)) return

    world.add(struckId, PlagiarizedSkill(skillId, copiedLevel))
    skillLists?.push(world, struckId)
  }

  /** A resolved cast of [skillId] at [skillLevel] uses the copy up, unless [casterId] learned it that high itself. */
  fun onCast(world: World, casterId: EntityId, skillId: Long, skillLevel: Int) {
    if (world.get(casterId, PlagiarizedSkill::class)?.skillId != skillId) return
    if (world.get(casterId, KnownSkills::class)?.knowsSkill(skillId, skillLevel) == true) return

    world.remove(casterId, PlagiarizedSkill::class)
    skillLists?.push(world, casterId)
  }

  /** The level [skillId] is copied at, or null when it cannot be copied at all. */
  private fun copiedLevel(skillId: Long, skillLevel: Int, plagiarismLevel: Int): Int? {
    val cap = attackLevelCap(plagiarismLevel)
    if (masterTree.findBySkillId(skillId) != null) {
      return minOf(skillLevel, cap / ATTACK_LEVELS_PER_SKILL_LEVEL).takeIf { it >= 1 }
    }
    val attackLevel = bestias.attackLevelOf(skillId) ?: return null

    return skillLevel.takeIf { attackLevel <= cap }
  }

  companion object {
    private const val MAX_LEVEL = 5
    private const val KEEPS_COPY_LEVEL = 5
    private const val ATTACK_LEVELS_PER_SKILL_LEVEL = 10

    fun chancePercent(level: Int): Int {
      return 10 * level.coerceIn(1, MAX_LEVEL)
    }

    /** The highest attack level copied at [level]; none at Lv. 5. */
    fun attackLevelCap(level: Int): Int {
      return if (level >= MAX_LEVEL) Int.MAX_VALUE else 20 * level.coerceAtLeast(1)
    }
  }
}
