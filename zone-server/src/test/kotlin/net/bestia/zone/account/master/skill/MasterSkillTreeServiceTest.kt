package net.bestia.zone.account.master.skill

import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.account.Master as MasterComponent
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.ecs.battle.status.SkillPoints
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.persistence.EntityWriteBehind
import net.bestia.zone.message.AccountTaskExecutor
import net.bestia.zone.skill.BasicSkillTooLowForTreeException
import net.bestia.zone.skill.NoSkillPointsAvailableException
import net.bestia.zone.skill.SkillPrerequisiteNotMetException
import net.bestia.zone.skill.SkillSubTreeNotUnlockedException
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import java.util.concurrent.CompletableFuture

/**
 * A small test tree standing in for `master_skill_tree.yml`: BASIC_SKILL in Novice, a Craftsman
 * trunk skill and one Blacksmith sub-tree skill with a prerequisite - just enough to exercise the
 * gates `investSkillPoints` enforces.
 */
class MasterSkillTreeServiceTest {

  private val world: EcsWorld = testWorld()
  private val writeBehind = mockk<EntityWriteBehind>(relaxed = true)
  private val masterSkillTreeRegistry = MasterSkillTreeRegistry()
  private val publishedEvents = mutableListOf<Any>()

  /** What the entity knew when each event went out: a listener asks the entity, not the event. */
  private val carpentryWhenPublished = mutableListOf<Int>()
  private val events = ApplicationEventPublisher { event ->
    publishedEvents.add(event)
    if (event is MasterSkillsChangedEvent) {
      carpentryWhenPublished.add(world.get(event.entityId, KnownSkills::class)?.levelOf(CARPENTRY_ID) ?: 0)
    }
  }

  /** Runs IO work at once, as if the account's inbox had nothing else queued. */
  private val accountTasks = object : AccountTaskExecutor {
    override fun onTick(accountId: AccountId, task: World.() -> Unit): CompletableFuture<Unit> {
      error("skill spends publish on the IO lane")
    }

    override fun onIo(accountId: AccountId, task: () -> Unit): CompletableFuture<Unit> {
      return CompletableFuture.completedFuture(task())
    }
  }

  private val service = MasterSkillTreeService(masterSkillTreeRegistry, writeBehind, accountTasks, events)

  init {
    masterSkillTreeRegistry.load(
      listOf(
        MasterSkillTreeNode(skillId = BASIC_SKILL_ID, identifier = "BASIC_SKILL", maxLevel = 5, tree = "NOVICE"),
        MasterSkillTreeNode(skillId = CARPENTRY_ID, identifier = "CARPENTRY", maxLevel = 10, tree = "CRAFTSMAN"),
        MasterSkillTreeNode(
          skillId = ORE_REFINEMENT_ID,
          identifier = "ORE_REFINEMENT",
          maxLevel = 3,
          tree = "CRAFTSMAN",
          subTree = "BLACKSMITH",
          prerequisites = listOf(MasterSkillPrerequisite(prerequisiteSkillId = CARPENTRY_ID, requiredLevel = 6))
        )
      )
    )
  }

  private fun givenMaster(skillPoints: Int = 99): EntityId {
    return world.createEntity { id ->
      add(id, MasterComponent(MASTER_ID, "novice"))
      add(id, SkillPoints(skillPoints))
      add(id, KnownSkills(mutableMapOf()))
    }
  }

  private fun invest(entityId: EntityId, vararg investments: Pair<Long, Int>): Map<Long, Int> {
    return service.investSkillPoints(
      world,
      ACCOUNT_ID,
      entityId,
      investments.map { (skillId, amount) -> SkillPointInvestment(skillId, amount) }
    )
  }

  private fun levelOf(entityId: EntityId, skillId: Long): Int {
    return world.get(entityId, KnownSkills::class)!!.levelOf(skillId)
  }

  private fun pointsOf(entityId: EntityId): Int {
    return world.get(entityId, SkillPoints::class)!!.value
  }

  @Test
  fun `a spend moves the components and hands the master to its write-behind`() {
    val entityId = givenMaster(skillPoints = 3)

    val changed = invest(entityId, BASIC_SKILL_ID to 2)

    assertEquals(mapOf(BASIC_SKILL_ID to 2), changed)
    assertEquals(2, levelOf(entityId, BASIC_SKILL_ID))
    assertEquals(1, pointsOf(entityId))
    verify(exactly = 1) { writeBehind.persist(world, listOf(entityId), withStatusEffects = false) }
  }

  @Test
  fun `the skills-changed event goes out only once the entity knows the new skill`() {
    val entityId = givenMaster()
    invest(entityId, BASIC_SKILL_ID to 5)
    publishedEvents.clear()
    carpentryWhenPublished.clear()

    invest(entityId, CARPENTRY_ID to 1)

    assertEquals(1, publishedEvents.filterIsInstance<MasterSkillsChangedEvent>().size)
    assertEquals(listOf(1), carpentryWhenPublished, "KnownSkills must already carry the investment")
  }

  @Test
  fun `a refused investment changes nothing, saves nothing and publishes nothing`() {
    val entityId = givenMaster(skillPoints = 7)
    publishedEvents.clear()

    assertThrows<BasicSkillTooLowForTreeException> {
      invest(entityId, BASIC_SKILL_ID to 1, CARPENTRY_ID to 1)
    }

    assertEquals(0, levelOf(entityId, BASIC_SKILL_ID), "the batch is checked whole before anything moves")
    assertEquals(7, pointsOf(entityId))
    assertTrue(publishedEvents.isEmpty())
    verify(exactly = 0) { writeBehind.persist(any(), any(), any(), any()) }
  }

  @Test
  fun `a batch larger than the points left is refused`() {
    val entityId = givenMaster(skillPoints = 2)

    assertThrows<NoSkillPointsAvailableException> {
      invest(entityId, BASIC_SKILL_ID to 3)
    }
    assertEquals(2, pointsOf(entityId))
  }

  @Test
  fun `a master below Basic Skill 5 cannot invest outside the Novice tree`() {
    val entityId = givenMaster()
    invest(entityId, BASIC_SKILL_ID to 4)

    assertThrows<BasicSkillTooLowForTreeException> {
      invest(entityId, CARPENTRY_ID to 1)
    }
    assertEquals(0, levelOf(entityId, CARPENTRY_ID))
  }

  /** Checked per level rather than once per batch, the same promise made for a prerequisite. */
  @Test
  fun `Basic Skill 5 and a point in another tree can be spent in one batch`() {
    val entityId = givenMaster()

    invest(entityId, BASIC_SKILL_ID to 5, CARPENTRY_ID to 1)

    assertEquals(5, levelOf(entityId, BASIC_SKILL_ID))
    assertEquals(1, levelOf(entityId, CARPENTRY_ID))
  }

  @Test
  fun `a sub-tree stays locked below 5 points spent in its parent tree`() {
    val entityId = givenMaster()
    invest(entityId, BASIC_SKILL_ID to 5, CARPENTRY_ID to 4)

    assertThrows<SkillSubTreeNotUnlockedException> {
      invest(entityId, ORE_REFINEMENT_ID to 1)
    }
  }

  @Test
  fun `a prerequisite is checked against the levels the batch has reached so far`() {
    val entityId = givenMaster()
    invest(entityId, BASIC_SKILL_ID to 5)

    assertThrows<SkillPrerequisiteNotMetException> {
      invest(entityId, CARPENTRY_ID to 5, ORE_REFINEMENT_ID to 1)
    }

    invest(entityId, CARPENTRY_ID to 6, ORE_REFINEMENT_ID to 1)
    assertEquals(1, levelOf(entityId, ORE_REFINEMENT_ID))
  }

  companion object {
    private const val ACCOUNT_ID = 1L
    private const val MASTER_ID = 7L
    private const val BASIC_SKILL_ID = 1L
    private const val CARPENTRY_ID = 3L
    private const val ORE_REFINEMENT_ID = 4L
  }
}
