package net.bestia.zone.boot

import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.SkillIds
import net.bestia.zone.skill.persistence.Skill
import net.bestia.zone.skill.persistence.SkillRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SkillIdsBootRunnerTest {

  private val repository = mockk<SkillRepository> {
    every { findAll() } returns listOf(skill("M_LUMBERJACK", 31L), skill("FIREBALL", 7L))
  }
  private val skillIds = SkillIds()

  @Test
  fun `a skill id read after boot asks no database`() {
    SkillIdsBootRunner(repository, skillIds).run()

    repeat(3) { assertEquals(31L, skillIds[SkillId.M_LUMBERJACK]) }
    assertNull(skillIds[SkillId.M_HAGGLING])

    verify(exactly = 1) { repository.findAll() }
    confirmVerified(repository)
  }

  @Test
  fun `a skill id read before boot fails rather than falling back to the database`() {
    assertThrows<IllegalStateException> { skillIds[SkillId.M_LUMBERJACK] }
  }

  private fun skill(identifier: String, id: Long) = mockk<Skill> {
    every { this@mockk.identifier } returns identifier
    every { this@mockk.id } returns id
  }
}
