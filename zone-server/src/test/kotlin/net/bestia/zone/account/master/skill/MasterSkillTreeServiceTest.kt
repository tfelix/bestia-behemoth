package net.bestia.zone.account.master.skill

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.account.Account
import net.bestia.zone.account.master.BodyType
import net.bestia.zone.account.master.Face
import net.bestia.zone.account.master.Hairstyle
import net.bestia.zone.account.master.Master
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.MasterResolver
import net.bestia.zone.battle.skill.SkillTargetType
import net.bestia.zone.ecs.account.Account as EcsAccount
import net.bestia.zone.ecs.battle.status.SkillPoints
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.skill.BasicSkillTooLowForTreeException
import net.bestia.zone.skill.LearnedSkill
import net.bestia.zone.skill.LearnedSkillRepository
import net.bestia.zone.skill.Skill
import net.bestia.zone.skill.SkillRepository
import net.bestia.zone.skill.SkillSubTreeNotUnlockedException
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.awt.Color
import java.util.Optional

/**
 * A small test tree standing in for `master_skill_tree.yml`: BASIC_SKILL in Novice, a Craftsman
 * trunk skill and one Blacksmith sub-tree skill - just enough to exercise the two gates
 * `investSkillPoints` enforces beyond a plain prerequisite check.
 */
class MasterSkillTreeServiceTest {

  private val world: World = testWorld()
  private val masterRepository = mockk<MasterRepository>(relaxed = true)
  private val masterResolver = mockk<MasterResolver>()
  private val skillRepository = mockk<SkillRepository>()
  private val learnedSkills = mutableListOf<LearnedSkill>()
  private val learnedSkillRepository = mockk<LearnedSkillRepository>()
  private val masterSkillTreeRegistry = MasterSkillTreeRegistry()

  private val service = MasterSkillTreeService(
    masterRepository = masterRepository,
    skillRepository = skillRepository,
    masterSkillTreeRegistry = masterSkillTreeRegistry,
    learnedSkillRepository = learnedSkillRepository,
    world = world,
    masterResolver = masterResolver
  )

  private val skills = listOf(
    skill(BASIC_SKILL_ID, "BASIC_SKILL"),
    skill(CARPENTRY_ID, "CARPENTRY"),
    skill(ORE_REFINEMENT_ID, "ORE_REFINEMENT")
  ).associateBy { it.identifier }

  init {
    masterSkillTreeRegistry.load(
      listOf(
        MasterSkillTreeNode(skillId = BASIC_SKILL_ID, maxLevel = 5, tree = "NOVICE"),
        MasterSkillTreeNode(skillId = CARPENTRY_ID, maxLevel = 10, tree = "CRAFTSMAN"),
        MasterSkillTreeNode(
          skillId = ORE_REFINEMENT_ID,
          maxLevel = 3,
          tree = "CRAFTSMAN",
          subTree = "BLACKSMITH"
        )
      )
    )

    every { skillRepository.findByIdentifier(any()) } answers { skills[firstArg<String>()] }
    every { skillRepository.findById(any()) } answers {
      Optional.ofNullable(skills.values.find { it.id == firstArg<Long>() })
    }

    every { learnedSkillRepository.save(any<LearnedSkill>()) } answers {
      val saved = firstArg<LearnedSkill>()
      learnedSkills.removeIf { it.skill.id == saved.skill.id }
      learnedSkills.add(saved)
      saved
    }
    every { learnedSkillRepository.findByMasterIdAndSkillId(any(), any()) } answers {
      val skillId = secondArg<Long>()
      learnedSkills.find { it.skill.id == skillId }
    }
    every { learnedSkillRepository.findAllByMasterId(any()) } answers { learnedSkills.toList() }
  }

  private fun givenMaster(skillPoints: Int = 99): Pair<Master, EntityId> {
    val master = Master(
      account = Account(1L),
      name = "novice",
      hairColor = Color.BLUE,
      skinColor = Color.BLUE,
      hair = Hairstyle.HAIR_1,
      face = Face.FACE_1,
      body = BodyType.BODY_M_1
    )

    val entityId = world.createEntity { id ->
      add(id, SkillPoints(skillPoints))
      add(id, EcsAccount(accountId = 1L))
    }

    every { masterRepository.findById(master.id) } returns Optional.of(master)
    every { masterRepository.save(any<Master>()) } answers { firstArg() }
    every { masterResolver.getEntityIdByMasterId(master.id) } returns entityId

    return master to entityId
  }

  private fun invest(masterId: Long, skillId: Long, amount: Int = 1) =
    service.investSkillPoints(masterId, listOf(SkillPointInvestment(skillId, amount)))

  private fun learnedLevelOf(skillId: Long): Int? = learnedSkills.find { it.skill.id == skillId }?.level

  @Test
  fun `a master with no Basic Skill at all cannot invest outside the Novice tree`() {
    val (master, _) = givenMaster()

    assertThrows<BasicSkillTooLowForTreeException> {
      invest(master.id, CARPENTRY_ID)
    }
    assertNull(learnedLevelOf(CARPENTRY_ID))
  }

  @Test
  fun `a master below Basic Skill 5 cannot invest outside the Novice tree`() {
    val (master, _) = givenMaster()
    invest(master.id, BASIC_SKILL_ID, 4)

    assertThrows<BasicSkillTooLowForTreeException> {
      invest(master.id, CARPENTRY_ID)
    }
    assertNull(learnedLevelOf(CARPENTRY_ID))
  }

  @Test
  fun `Basic Skill 5 unlocks the other trees`() {
    val (master, _) = givenMaster()
    invest(master.id, BASIC_SKILL_ID, 5)

    invest(master.id, CARPENTRY_ID)

    assertEquals(1, learnedLevelOf(CARPENTRY_ID))
  }

  /**
   * The gate is checked per level rather than once per batch precisely so this works - the same
   * promise `investSkillPoints` already makes for a prerequisite satisfied earlier in the batch.
   */
  @Test
  fun `Basic Skill 5 and a point in another tree can be spent in one batch`() {
    val (master, _) = givenMaster()

    service.investSkillPoints(
      master.id,
      listOf(SkillPointInvestment(BASIC_SKILL_ID, 5), SkillPointInvestment(CARPENTRY_ID, 1))
    )

    assertEquals(5, learnedLevelOf(BASIC_SKILL_ID))
    assertEquals(1, learnedLevelOf(CARPENTRY_ID))
  }

  @Test
  fun `a sub-tree stays locked below 5 points spent in its parent tree`() {
    val (master, _) = givenMaster()
    invest(master.id, BASIC_SKILL_ID, 5)
    invest(master.id, CARPENTRY_ID, 4)

    assertThrows<SkillSubTreeNotUnlockedException> {
      invest(master.id, ORE_REFINEMENT_ID)
    }
    assertNull(learnedLevelOf(ORE_REFINEMENT_ID))
  }

  @Test
  fun `a sub-tree unlocks once 5 points are spent anywhere in its parent tree`() {
    val (master, _) = givenMaster()
    invest(master.id, BASIC_SKILL_ID, 5)
    invest(master.id, CARPENTRY_ID, 5)

    invest(master.id, ORE_REFINEMENT_ID)

    assertEquals(1, learnedLevelOf(ORE_REFINEMENT_ID))
  }

  private fun skill(id: Long, identifier: String) = Skill(
    id = id,
    identifier = identifier,
    strength = null,
    script = null,
    manaCost = 0,
    range = null,
    targetType = SkillTargetType.FRIENDLY,
    needsLineOfSight = false,
    requiredLevel = 0
  )

  companion object {
    private const val BASIC_SKILL_ID = 1L
    private const val CARPENTRY_ID = 3L
    private const val ORE_REFINEMENT_ID = 4L
  }
}
