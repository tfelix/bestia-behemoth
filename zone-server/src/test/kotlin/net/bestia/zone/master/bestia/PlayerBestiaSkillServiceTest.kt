package net.bestia.zone.master.bestia

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.account.persistence.LearnedSkill
import net.bestia.zone.account.persistence.LearnedSkillRepository
import net.bestia.zone.account.persistence.PlayerBestia
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.bestia.persistence.Bestia
import net.bestia.zone.skill.persistence.Skill
import net.bestia.zone.skill.persistence.SkillRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Optional
import net.bestia.zone.bestia.persistence.LearnedSkill as LearnsetEntry

class PlayerBestiaSkillServiceTest {

  private val species = Bestia(id = SPECIES_ID, identifier = "blob", level = 3, experienceReward = 5, health = 10, mana = 8)

  private val playerBestia = mockk<PlayerBestia> {
    every { bestia } returns species
  }
  private val playerBestiaRepository = mockk<PlayerBestiaRepository> {
    every { findById(PLAYER_BESTIA_ID) } returns Optional.of(playerBestia)
  }
  private val learnedSkillRepository = mockk<LearnedSkillRepository>()
  private val skillRepository = mockk<SkillRepository> {
    every { getReferenceById(any()) } answers { skillWithId(firstArg()) }
  }
  private val catalogue = mockk<BestiaCatalogue>()

  private val saved = mutableListOf<LearnedSkill>()

  private val sut = PlayerBestiaSkillService(playerBestiaRepository, learnedSkillRepository, skillRepository, catalogue)

  init {
    every { learnedSkillRepository.save(any<LearnedSkill>()) } answers {
      saved.add(firstArg())
      firstArg()
    }
  }

  private fun skillWithId(skillId: Long): Skill {
    return mockk { every { id } returns skillId }
  }

  private fun row(skillId: Long, skillLevel: Int): LearnedSkill {
    return mockk {
      every { skill } returns skillWithId(skillId)
      every { level } returns skillLevel
    }
  }

  private fun givenLearnset(vararg entries: Pair<Long, Int>) {
    every { catalogue.learnsetUpTo(SPECIES_ID, PlayerBestiaSkillService.MAX_BESTIA_LEVEL) } returns
      entries.map { (skillId, requiredLevel) -> LearnsetEntry(SPECIES_ID, skillId, requiredLevel) }
  }

  @Test
  fun `a bestia learns every skill of its learnset at level 1 whatever its own level`() {
    givenLearnset(EMBER to 3, LATER to 90)
    every { learnedSkillRepository.findAllByPlayerBestiaId(PLAYER_BESTIA_ID) } returns emptyList()

    val custom = sut.learnWholeLearnset(PLAYER_BESTIA_ID)

    assertEquals(mapOf(EMBER to 1, LATER to 1), custom)
    assertEquals(listOf(EMBER, LATER), saved.map { it.skill.id })
    assertEquals(listOf(1, 1), saved.map { it.level })
    assertEquals(listOf(playerBestia, playerBestia), saved.map { it.playerBestia })
  }

  @Test
  fun `a skill the bestia already has a row for is kept at its level`() {
    givenLearnset(EMBER to 3, LATER to 90)
    every { learnedSkillRepository.findAllByPlayerBestiaId(PLAYER_BESTIA_ID) } returns listOf(row(EMBER, 4))

    val custom = sut.learnWholeLearnset(PLAYER_BESTIA_ID)

    assertEquals(mapOf(EMBER to 4, LATER to 1), custom)
    assertEquals(listOf(LATER), saved.map { it.skill.id })
  }

  @Test
  fun `learning again saves nothing`() {
    givenLearnset(EMBER to 3)
    every { learnedSkillRepository.findAllByPlayerBestiaId(PLAYER_BESTIA_ID) } returns listOf(row(EMBER, 1))

    val custom = sut.learnWholeLearnset(PLAYER_BESTIA_ID)

    assertEquals(mapOf(EMBER to 1), custom)
    verify(exactly = 0) { learnedSkillRepository.save(any<LearnedSkill>()) }
  }

  @Test
  fun `a species without a learnset leaves custom skills as they are`() {
    givenLearnset()
    every { learnedSkillRepository.findAllByPlayerBestiaId(PLAYER_BESTIA_ID) } returns listOf(row(EMBER, 2))

    assertEquals(mapOf(EMBER to 2), sut.learnWholeLearnset(PLAYER_BESTIA_ID))
    verify(exactly = 0) { learnedSkillRepository.save(any<LearnedSkill>()) }
  }

  private companion object {
    const val PLAYER_BESTIA_ID = 11L
    const val SPECIES_ID = 1L
    const val EMBER = 1000L
    const val LATER = 1001L
  }
}
