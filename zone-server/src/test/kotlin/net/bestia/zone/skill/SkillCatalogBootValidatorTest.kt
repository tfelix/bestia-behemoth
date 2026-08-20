package net.bestia.zone.skill

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.battle.skill.SkillTargetType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.core.io.ClassPathResource

/**
 * Runs the validator over the real `skills.yml`, so renaming or deleting a skill that code names
 * fails here rather than at server boot - the boot check needs a database and a full context, which
 * is exactly the round trip nobody makes before pushing.
 */
class SkillCatalogBootValidatorTest {

  @Test
  fun `the shipped skills-yml has a row for every SkillId`() {
    val shipped = shippedIdentifiers()
    assertTrue(shipped.isNotEmpty(), "skills.yml should not be empty")

    assertDoesNotThrow { validatorOver(shipped).validateSkillCatalog() }
  }

  @Test
  fun `a SkillId with no catalogue row fails the boot`() {
    val withoutCarpentry = shippedIdentifiers() - SkillId.CARPENTRY.name

    val e = assertThrows<SkillCatalogMismatchException> {
      validatorOver(withoutCarpentry).validateSkillCatalog()
    }

    assertTrue(e.message!!.contains("SkillId.CARPENTRY"), "should name the orphaned constant: ${e.message}")
  }

  /** Every constant is named, not only the first one to miss - one boot should report all the drift. */
  @Test
  fun `an empty catalogue names every constant`() {
    val e = assertThrows<SkillCatalogMismatchException> { validatorOver(emptySet()).validateSkillCatalog() }

    SkillId.entries.forEach { skill ->
      assertTrue(e.message!!.contains("SkillId.${skill.name}"), "should name ${skill.name}: ${e.message}")
    }
  }

  /**
   * The deliberate asymmetry with [net.bestia.zone.dialog.DialogCatalogBootValidator]: content that
   * no code names is the normal case for most of the catalogue, so adding a skill must stay a
   * content-only edit.
   */
  @Test
  fun `a catalogued skill with no SkillId constant is not an error`() {
    val withNewContent = SkillId.entries.mapTo(mutableSetOf()) { it.name } + "SOME_NEW_CONTENT_SKILL"

    assertDoesNotThrow { validatorOver(withNewContent).validateSkillCatalog() }
  }

  private fun validatorOver(identifiers: Set<String>): SkillCatalogBootValidator {
    val repository = mockk<SkillRepository>()
    every { repository.findAll() } returns identifiers.map { skill(it) }

    return SkillCatalogBootValidator(repository)
  }

  private fun skill(identifier: String) = Skill(
    id = 1,
    identifier = identifier,
    strength = null,
    script = null,
    manaCost = 0,
    range = null,
    targetType = SkillTargetType.FRIENDLY,
    needsLineOfSight = false,
    requiredLevel = 0
  )

  private fun shippedIdentifiers(): Set<String> {
    val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    val tree = ClassPathResource("skills.yml").inputStream.use { mapper.readTree(it) }

    return tree["skills"].mapTo(mutableSetOf()) { it["identifier"].asText() }
  }
}
