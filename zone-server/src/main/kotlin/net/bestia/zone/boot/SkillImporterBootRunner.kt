package net.bestia.zone.boot

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.skill.Skill
import net.bestia.zone.skill.SkillRepository
import net.bestia.zone.battle.skill.SkillTargetType
import net.bestia.zone.battle.skill.SkillType
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/**
 * Imports the items from the YML resources into the database.
 */
@Component
@Order(102)
class SkillImporterBootRunner(
  skillRepository: SkillRepository,
) : CommandLineRunner,
  YmlImporterBootRunner<SkillImporterBootRunner.SkillYmlDto, Skill>(
    "Skill",
    "skill",
    skillRepository,
    SkillYmlDto::class.java
  ) {
  data class SkillYmlDto(
    val id: Long,
    val identifier: String,
    val strength: Int? = null,
    val manaCost: Int = 0,
    val type: SkillType,
    val script: String? = null,
    val range: Int? = null,
    val targetType: SkillTargetType,
    val aoeRadius: Double? = null,
    val needsLineOfSight: Boolean = false,
    val castTime: Float = 0f,
    val requiredLevel: Int = 0,
    val description: String? = null
  )

  /**
   * Wrapper for the single `skills.yml` file which holds all skills under a top-level `skills` list.
   */
  data class SkillsYmlFile(
    val skills: List<SkillYmlDto> = emptyList()
  )

  override fun loadYmlItems(): List<SkillYmlDto> {
    val objectMapper = createYmlMapper()

    ClassPathResource(SKILLS_RESOURCE).inputStream.use { stream ->
      return objectMapper.readValue(stream, SkillsYmlFile::class.java).skills
    }
  }

  override fun newEntity(dto: SkillYmlDto): Skill {

    return Skill(
      id = dto.id,
      identifier = dto.identifier,
      strength = dto.strength,
      type = dto.type,
      script = dto.script,
      manaCost = dto.manaCost,
      range = dto.range,
      targetType = dto.targetType,
      aoeRadius = dto.aoeRadius,
      needsLineOfSight = dto.needsLineOfSight,
      castTime = dto.castTime,
      requiredLevel = dto.requiredLevel,
      description = dto.description
    )
  }

  override fun getEntityIdentifier(entity: Skill): String {
    return entity.identifier
  }

  override fun getYmlIdentifier(dto: SkillYmlDto): String {
    return dto.identifier
  }

  override fun getYmlId(dto: SkillYmlDto): Long {
    return dto.id
  }

  override fun tryUpdate(dto: SkillYmlDto, entity: Skill): Boolean {
    // TODO no sure if we should even support updates
    return false
  }

  override fun postImport(entities: List<Skill>) {
    // TODO verify all skills which required a script actually have a script.
  }

  companion object {
    private const val SKILLS_RESOURCE = "skills.yml"

    private val LOG = KotlinLogging.logger { }
  }
}
