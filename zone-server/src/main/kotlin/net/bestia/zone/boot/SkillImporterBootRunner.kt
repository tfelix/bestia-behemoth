package net.bestia.zone.boot

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.skill.Skill
import net.bestia.zone.battle.skill.SkillRepository
import net.bestia.zone.battle.skill.SkillType
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
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
    val strength: Int?,
    val manaCost: Int,
    val type: SkillType,
    val script: String?,
    val range: Int?,
    val needsLineOfSight: Boolean,
    val requiredLevel: Int = 0
  )

  override fun newEntity(dto: SkillYmlDto): Skill {

    return Skill(
      id = dto.id,
      identifier = dto.identifier,
      strength = dto.strength,
      type = dto.type,
      script = dto.script,
      manaCost = dto.manaCost,
      range = dto.range,
      needsLineOfSight = dto.needsLineOfSight,
      requiredLevel = dto.requiredLevel
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
    private val LOG = KotlinLogging.logger { }
  }
}
