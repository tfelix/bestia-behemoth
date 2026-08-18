package net.bestia.zone.account.master.skill

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.skill.Skill
import net.bestia.zone.skill.SkillRepository
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The two Basic Skill ranks the game actually enforces, and what happens at the edges of them.
 *
 * The ranks come straight off the design docs, so the assertions are written against the documented numbers
 * rather than against the constants - a test that reads `CHAT_RANK` on both sides would pass whatever the
 * number became.
 */
class BasicSkillGateTest {

  private val world: World = testWorld()
  private val skillRepository = mockk<SkillRepository>()
  private val connectionInfoService = mockk<ConnectionInfoService>()

  private val gate: BasicSkillGate

  init {
    every { skillRepository.findByIdentifier("BASIC_SKILL") } returns
      mockk<Skill>().also { every { it.id } returns BASIC_SKILL_ID }

    gate = BasicSkillGate(worldViewOf(world), connectionInfoService, skillRepository)
  }

  @Test
  fun `a fresh master may neither chat nor party`() {
    givenActiveEntity(basicSkillLevel = 0)

    assertEquals(0, gate.rankOf(ACCOUNT_ID))
    assertFalse(gate.mayChat(ACCOUNT_ID))
    assertFalse(gate.mayParty(ACCOUNT_ID))
  }

  @Test
  fun `rank one buys trading and still no chat`() {
    givenActiveEntity(basicSkillLevel = 1)

    assertFalse(gate.mayChat(ACCOUNT_ID))
  }

  @Test
  fun `chat opens at rank two`() {
    givenActiveEntity(basicSkillLevel = 2)

    assertTrue(gate.mayChat(ACCOUNT_ID))
    assertFalse(gate.mayParty(ACCOUNT_ID))
  }

  @Test
  fun `parties open at rank five`() {
    givenActiveEntity(basicSkillLevel = 4)
    assertFalse(gate.mayParty(ACCOUNT_ID))

    givenActiveEntity(basicSkillLevel = 5)
    assertTrue(gate.mayParty(ACCOUNT_ID))
  }

  /** A bestia has a `KnownSkills` of its own and no Basic Skill in it, so the gate is closed while riding it. */
  @Test
  fun `an entity with no Basic Skill at all reads as rank zero`() {
    val entityId = world.createEntity { id -> add(id, KnownSkills(mutableMapOf(999L to 5))) }
    every { connectionInfoService.getActiveEntityId(ACCOUNT_ID) } returns entityId

    assertEquals(0, gate.rankOf(ACCOUNT_ID))
  }

  @Test
  fun `an entity with no KnownSkills component reads as rank zero`() {
    val entityId = world.createEntity { }
    every { connectionInfoService.getActiveEntityId(ACCOUNT_ID) } returns entityId

    assertEquals(0, gate.rankOf(ACCOUNT_ID))
  }

  /** A message can arrive from a connection that has just gone away; "no" is the right answer, not a throw. */
  @Test
  fun `an account with no live session is refused rather than failing`() {
    every { connectionInfoService.getActiveEntityId(ACCOUNT_ID) } throws IllegalStateException("gone")

    assertFalse(gate.mayChat(ACCOUNT_ID))
    assertFalse(gate.mayParty(ACCOUNT_ID))
  }

  /**
   * A catalogue without BASIC_SKILL is a content error, and taking chat away from every player because of one
   * would be far worse than letting everybody talk.
   */
  @Test
  fun `a missing Basic Skill in the catalogue opens every gate`() {
    val emptyCatalogue = mockk<SkillRepository>()
    every { emptyCatalogue.findByIdentifier(any()) } returns null

    val openGate = BasicSkillGate(worldViewOf(world), connectionInfoService, emptyCatalogue)

    assertTrue(openGate.mayChat(ACCOUNT_ID))
    assertTrue(openGate.mayParty(ACCOUNT_ID))
  }

  private fun givenActiveEntity(basicSkillLevel: Int): EntityId {
    val entityId = world.createEntity { id ->
      add(id, KnownSkills(mutableMapOf(BASIC_SKILL_ID to basicSkillLevel)))
    }
    every { connectionInfoService.getActiveEntityId(ACCOUNT_ID) } returns entityId

    return entityId
  }

  /**
   * The gate only ever calls [WorldView.read], so the view is stubbed to run the block against the test world
   * directly rather than standing up the locking wrapper a real zone has.
   */
  private fun worldViewOf(world: World): WorldView = mockk<WorldView>().also { view ->
    every { view.read<Any?>(any()) } answers { firstArg<World.() -> Any?>().invoke(world) }
  }

  private companion object {
    const val ACCOUNT_ID = 1L
    const val BASIC_SKILL_ID = 6L
  }
}
