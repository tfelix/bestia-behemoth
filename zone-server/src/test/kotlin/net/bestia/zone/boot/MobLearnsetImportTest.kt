package net.bestia.zone.boot

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.zone.bestia.persistence.Bestia
import net.bestia.zone.bestia.persistence.BestiaSkill
import net.bestia.zone.bestia.persistence.BestiaSkillRepository
import net.bestia.zone.boot.MobImporterBootRunner.MobYmlDto.LearnedAttack
import net.bestia.zone.skill.persistence.Skill
import net.bestia.zone.skill.persistence.SkillRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MobLearnsetImportTest {

  private val ember = mockk<Skill> { every { id } returns 1000L }
  private val skillRepository = mockk<SkillRepository> {
    every { findByIdentifier("ember") } returns ember
    every { findByIdentifier("nonsense") } returns null
  }
  private val bestiaSkillRepository = mockk<BestiaSkillRepository>(relaxed = true)
  private val importer = MobImporterBootRunner(mockk(), skillRepository, bestiaSkillRepository, mockk())
  private val blob = Bestia(id = 1, identifier = "blob", level = 3, experienceReward = 5, health = 10, mana = 8)

  @Test
  fun `a new learnset is written with its learn levels`() {
    every { bestiaSkillRepository.findAllByBestiaId(1) } returns emptyList()
    val saved = slot<Iterable<BestiaSkill>>()
    every { bestiaSkillRepository.saveAll(capture(saved)) } answers { saved.captured.toList() }

    importer.syncLearnset(blob, listOf(LearnedAttack(skill = "ember", level = 3)))

    val written = saved.captured.single()
    assertEquals(ember, written.skill)
    assertEquals(3, written.requiredLevel)
  }

  @Test
  fun `an unchanged learnset is left alone`() {
    every { bestiaSkillRepository.findAllByBestiaId(1) } returns listOf(BestiaSkill(blob, ember, 3))

    importer.syncLearnset(blob, listOf(LearnedAttack(skill = "ember", level = 3)))

    verify(exactly = 0) { bestiaSkillRepository.saveAll(any<Iterable<BestiaSkill>>()) }
    verify(exactly = 0) { bestiaSkillRepository.deleteAll(any<Iterable<BestiaSkill>>()) }
  }

  @Test
  fun `a learn level moved in the YAML replaces the stored one`() {
    val stored = listOf(BestiaSkill(blob, ember, 3))
    every { bestiaSkillRepository.findAllByBestiaId(1) } returns stored

    importer.syncLearnset(blob, listOf(LearnedAttack(skill = "ember", level = 5)))

    verify { bestiaSkillRepository.deleteAll(stored) }
    verify { bestiaSkillRepository.saveAll(match<Iterable<BestiaSkill>> { it.single().requiredLevel == 5 }) }
  }

  @Test
  fun `an unknown skill fails the import`() {
    assertThrows<IllegalArgumentException> {
      importer.syncLearnset(blob, listOf(LearnedAttack(skill = "nonsense", level = 1)))
    }
  }
}
