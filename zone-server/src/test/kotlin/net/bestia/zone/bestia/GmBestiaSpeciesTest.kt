package net.bestia.zone.bestia

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import net.bestia.zone.boot.MobImporterBootRunner
import net.bestia.zone.boot.SkillImporterBootRunner
import net.bestia.zone.item.equip.ArmorType
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.EquipmentSlots
import net.bestia.zone.master.bestia.GmBestiaGrant
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Holds the real `mob/gm-bestia.yml` against what the GM bestia is promised to be, and against `skills.yml` so a
 * bestia attack added later cannot be left out of its learnset unnoticed.
 */
class GmBestiaSpeciesTest {

  private val species = readSpecies()

  @Test
  fun `it is the species the grant hands out`() {
    assertEquals(GmBestiaGrant.SPECIES, species.identifier)
  }

  @Test
  fun `it starts at level 80`() {
    assertEquals(80, species.level)
  }

  @Test
  fun `no wild den or ambient site may place it`() {
    assertTrue(species.eventOnly)
  }

  @Test
  fun `every attribute is well above anything a master can build`() {
    val attributes = species.attributes

    listOf(
      attributes.strength,
      attributes.intelligence,
      attributes.vitality,
      attributes.dexterity,
      attributes.willpower,
      attributes.agility
    ).forEach { assertTrue(it >= MIN_ATTRIBUTE, "an attribute is $it, wanted at least $MIN_ATTRIBUTE") }
  }

  @Test
  fun `it has every equipment slot and every armor type`() {
    val slots = species.equipSlots.map { EquipmentSlot.valueOf(it.uppercase()) }

    assertEquals(EquipmentSlots.ALL, EquipmentSlots.maskOf(slots))
    assertEquals(ArmorType.ALL, ArmorType.maskOf(species.armorTypes))
  }

  @Test
  fun `it learns every attack in the bestia range at a level it already has`() {
    val bestiaAttacks = readSkills().filter { it.id >= FIRST_BESTIA_SKILL_ID }.map { it.identifier }.toSet()

    assertTrue(bestiaAttacks.isNotEmpty(), "skills.yml no longer has a bestia range, so this test checks nothing")
    assertEquals(bestiaAttacks, species.learnset.map { it.skill }.toSet())
    assertTrue(species.learnset.all { it.level <= species.level })
  }

  private fun mapper(): ObjectMapper {
    return ObjectMapper(YAMLFactory())
      .registerKotlinModule()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
  }

  private fun readSpecies(): MobImporterBootRunner.MobYmlDto {
    return ClassPathResource("mob/gm-bestia.yml").inputStream.use {
      mapper().readValue(it, MobImporterBootRunner.MobYmlDto::class.java)
    }
  }

  private fun readSkills(): List<SkillImporterBootRunner.SkillYmlDto> {
    return ClassPathResource("skills.yml").inputStream.use {
      mapper().readValue(it, SkillImporterBootRunner.SkillsYmlFile::class.java).skills
    }
  }

  private companion object {
    const val MIN_ATTRIBUTE = 80

    /** `skills.yml` reserves 1000 and up for the skills of bestia species. */
    const val FIRST_BESTIA_SKILL_ID = 1000L
  }
}
