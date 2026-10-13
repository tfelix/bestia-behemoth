package net.bestia.zone.master.skill

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.account.persistence.LearnedSkill
import net.bestia.zone.account.persistence.LearnedSkillRepository
import net.bestia.zone.account.persistence.PlayerBestia
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.bestia.persistence.Bestia
import net.bestia.zone.bestia.persistence.BestiaSkill
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.persistence.Skill
import net.bestia.zone.skill.tree.MasterSkillTreeNode
import net.bestia.zone.skill.tree.MasterSkillTreeRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Optional

class SkillListServiceTest {

  private val world = testWorld()
  private val connectionInfo = mockk<ConnectionInfoService>()
  private val playerBestiaRepository = mockk<PlayerBestiaRepository>()
  private val learnedSkillRepository = mockk<LearnedSkillRepository>()
  private val masterSkillTree = MasterSkillTreeRegistry().apply {
    load(listOf(MasterSkillTreeNode(skillId = BLESSING, identifier = "BLESSING", maxLevel = 10, tree = "NOVICE")))
  }

  private val sut = SkillListService(
    connectionInfo,
    playerBestiaRepository,
    learnedSkillRepository,
    MasterSkillListBuilder(masterSkillTree),
    world
  )

  private val species = Bestia(id = 1L, identifier = "blob", level = 3, experienceReward = 5, health = 10, mana = 8)

  init {
    every { connectionInfo.getSelectedMasterEntityId(ACCOUNT) } returns MASTER_ENTITY
    every { connectionInfo.getMasterId(ACCOUNT) } returns 7L
    every { connectionInfo.getOwnedEntitiesByMaster(ACCOUNT, 7L) } returns
      setOf(ConnectionInfoService.PlayerEntity(PLAYER_BESTIA, BESTIA_ENTITY))
    every { learnedSkillRepository.findAllByPlayerBestiaId(PLAYER_BESTIA) } returns emptyList()
  }

  private fun givenBestia(bestiaLevel: Int, vararg learnset: Pair<Long, Int>) {
    learnset.forEach { (skillId, gate) ->
      species.skills.add(
        mockk<BestiaSkill> {
          every { skill } returns skillWithId(skillId)
          every { requiredLevel } returns gate
        }
      )
    }
    every { playerBestiaRepository.findById(PLAYER_BESTIA) } returns Optional.of(
      mockk<PlayerBestia> {
        every { bestia } returns species
        every { level } returns bestiaLevel
      }
    )
  }

  private fun givenCustom(skillId: Long, customLevel: Int) {
    every { learnedSkillRepository.findAllByPlayerBestiaId(PLAYER_BESTIA) } returns listOf(
      mockk<LearnedSkill> {
        every { skill } returns skillWithId(skillId)
        every { level } returns customLevel
      }
    )
  }

  private fun skillWithId(skillId: Long): Skill {
    return mockk { every { id } returns skillId }
  }

  private fun bestiaLevels(): Map<Long, Int> {
    return sut.entriesFor(ACCOUNT, BESTIA_ENTITY).associate { it.skillId to it.level }
  }

  @Test
  fun `a master sees the whole tree at the levels its KnownSkills holds`() {
    world.createEntity(MASTER_ENTITY) { id -> add(id, KnownSkills(mutableMapOf(BLESSING to 4))) }

    assertEquals(
      listOf(SkillListSMSG.SkillListEntry(BLESSING, 4)),
      sut.entriesFor(ACCOUNT, MASTER_ENTITY)
    )
  }

  @Test
  fun `a species skill is learned by level and locked above it`() {
    givenBestia(bestiaLevel = 3, EMBER to 3, LATER to 4)

    assertEquals(mapOf(EMBER to 1, LATER to 0), bestiaLevels())
  }

  @Test
  fun `a custom row teaches a species skill ahead of its level`() {
    givenBestia(bestiaLevel = 1, EMBER to 3, LATER to 90)
    givenCustom(LATER, 1)

    assertEquals(mapOf(EMBER to 0, LATER to 1), bestiaLevels())
  }

  @Test
  fun `a custom row above the level the table gives wins`() {
    givenBestia(bestiaLevel = 5, EMBER to 3)
    givenCustom(EMBER, 4)

    assertEquals(mapOf(EMBER to 4), bestiaLevels())
  }

  @Test
  fun `a custom skill outside the species table is listed after it`() {
    givenBestia(bestiaLevel = 3, EMBER to 3)
    givenCustom(OTHER, 2)

    assertEquals(listOf(EMBER to 1, OTHER to 2), sut.entriesFor(ACCOUNT, BESTIA_ENTITY).map { it.skillId to it.level })
  }

  private companion object {
    const val ACCOUNT = 1L
    const val MASTER_ENTITY = 100L
    const val BESTIA_ENTITY = 200L
    const val PLAYER_BESTIA = 11L
    const val BLESSING = 5L
    const val EMBER = 1000L
    const val LATER = 1001L
    const val OTHER = 1002L
  }
}
