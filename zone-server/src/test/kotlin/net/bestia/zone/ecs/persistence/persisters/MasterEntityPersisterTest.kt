package net.bestia.zone.ecs.persistence.persisters

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.zone.account.master.BodyType
import net.bestia.zone.account.master.Face
import net.bestia.zone.account.master.Hairstyle
import net.bestia.zone.account.master.Master
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.skill.MasterSkillTreeNode
import net.bestia.zone.account.master.skill.MasterSkillTreeRegistry
import net.bestia.zone.identity.ecs.Master as MasterComponent
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.ecs.battle.exp.Exp
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.skill.LearnedSkill
import net.bestia.zone.skill.LearnedSkillRepository
import net.bestia.zone.skill.Skill
import net.bestia.zone.skill.SkillRepository
import org.junit.jupiter.api.Test
import java.awt.Color
import kotlin.test.assertEquals

/**
 * What a master takes with it out of the world - in particular that leaving while dead resolves the
 * respawn on the way out, rather than storing the spot it was killed on.
 */
class MasterEntityPersisterTest {

  private val masterId = 5L
  private val savePoint = Vec3L(10, 20, 30)

  private val row = Master(
    account = mockk(relaxed = true),
    name = "Tester",
    hairColor = Color.BLACK,
    skinColor = Color.WHITE,
    hair = Hairstyle.entries.first(),
    face = Face.entries.first(),
    body = BodyType.entries.first(),
  ).also { it.spawnPosition = savePoint }

  private val repository = mockk<MasterRepository> {
    every { findByIdForUpdate(any()) } returns row
    every { save(any()) } returns row
  }

  private val learnedSkills = mockk<LearnedSkillRepository>(relaxed = true) {
    every { findAllByMasterId(any()) } returns emptyList()
  }
  private val skills = mockk<SkillRepository> {
    every { getReferenceById(any()) } answers { mockk<Skill> { every { id } returns firstArg() } }
  }
  private val skillTree = MasterSkillTreeRegistry().apply {
    load(listOf(TREE_SKILL_ID, OTHER_TREE_SKILL_ID).map { MasterSkillTreeNode(it, "SKILL_$it", 5, "NOVICE") })
  }

  private val sut = MasterEntityPersister(repository, learnedSkills, skills, skillTree)

  private fun World.master(dead: Boolean) = createEntity { eid ->
    add(eid, MasterComponent(masterId, "Tester"))
    add(eid, Position(70, 71, 72))
    add(eid, Health(current = if (dead) 0 else 123, max = 200))
    if (dead) add(eid, Dead())
  }

  @Test
  fun `a living master keeps where it stood and the health it had`() {
    val world = testWorld()
    val id = world.master(dead = false)

    sut.persist(listOfNotNull(sut.snapshot(world, id)))

    assertEquals(Vec3L(70, 71, 72), row.currentPosition)
    assertEquals(123, row.currentHealth)
  }

  @Test
  fun `a master that left dead is stored at its save point with one hit point`() {
    val world = testWorld()
    val id = world.master(dead = true)

    sut.persist(listOfNotNull(sut.snapshot(world, id)))

    assertEquals(savePoint, row.currentPosition)
    assertEquals(1, row.currentHealth)
  }

  @Test
  fun `the exp towards the next level is stored with the master`() {
    val world = testWorld()
    val id = world.master(dead = false)
    world.add(id, Exp(345))

    sut.persist(listOfNotNull(sut.snapshot(world, id)))

    assertEquals(345, row.exp)
  }

  @Test
  fun `a master's writes are keyed by the master, not by the shared entity rows`() {
    val world = testWorld()
    val id = world.master(dead = false)

    assertEquals(masterId, sut.snapshot(world, id)?.writeKey)
  }

  @Test
  fun `learned levels are saved with the points that bought them`() {
    val world = testWorld()
    val id = world.master(dead = false)
    world.add(id, KnownSkills(mutableMapOf(TREE_SKILL_ID to 2, OTHER_TREE_SKILL_ID to 1)))
    val known = mockk<LearnedSkill>(relaxed = true) {
      every { skill } returns mockk { every { this@mockk.id } returns OTHER_TREE_SKILL_ID }
      every { level } returns 1
    }
    every { learnedSkills.findAllByMasterId(any()) } returns listOf(known)
    val inserted = slot<LearnedSkill>()
    every { learnedSkills.save(capture(inserted)) } answers { firstArg() }

    sut.persist(listOfNotNull(sut.snapshot(world, id)))

    // The new skill is inserted; the one already stored at its level is left alone.
    verify(exactly = 1) { learnedSkills.save(any()) }
    assertEquals(TREE_SKILL_ID, inserted.captured.skill.id)
    assertEquals(2, inserted.captured.level)
  }

  @Test
  fun `a skill the tree no longer has is not written`() {
    val world = testWorld()
    val id = world.master(dead = false)
    world.add(id, KnownSkills(mutableMapOf(GONE_SKILL_ID to 3)))

    sut.persist(listOfNotNull(sut.snapshot(world, id)))

    verify(exactly = 0) { learnedSkills.save(any()) }
  }

  private companion object {
    const val TREE_SKILL_ID = 1L
    const val OTHER_TREE_SKILL_ID = 2L
    const val GONE_SKILL_ID = 99L
  }
}
