package net.bestia.zone.boot

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ElementModifier
import net.bestia.zone.skill.SkillArchetype
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.skill.persistence.Skill
import net.bestia.zone.skill.persistence.SkillYield
import net.bestia.zone.skill.persistence.SkillRepository
import net.bestia.zone.skill.SkillTargetType
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/**
 * Imports the items from the YML resources into the database.
 */
@Component
@Order(101)
class SkillImporterBootRunner(
  skillRepository: SkillRepository,
  private val itemRepository: ItemRepository,
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
    val script: String? = null,
    val range: Int? = null,
    val targetType: SkillTargetType,
    val aoeRadius: Double? = null,
    val needsLineOfSight: Boolean = false,
    val castTime: Float = 0f,
    val requiredLevel: Int = 0,
    val description: String? = null,
    val name: String? = null,
    val archetype: SkillArchetype? = null,
    val element: Element = Element.NORMAL,
    val power: Float = 1f,
    val cooldown: Float = 0f,
    val status: StatusEffectId? = null,
    val yields: List<YieldDto> = emptyList(),
    val lines: List<String> = emptyList()
  ) {
    data class YieldDto(
      val item: String,
      val chance: Int
    )
  }

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
    // Here rather than in Skill: a fight would only find out on the tick thread, see BattleAttack.
    require(ElementModifier.isLegalAttackElement(dto.element)) {
      "Skill ${dto.identifier}: ${dto.element} cannot be an attack element - only level 1 elements can be"
    }

    return Skill(
      id = dto.id,
      identifier = dto.identifier,
      strength = dto.strength,
      script = dto.script,
      manaCost = dto.manaCost,
      range = dto.range,
      targetType = dto.targetType,
      aoeRadius = dto.aoeRadius,
      needsLineOfSight = dto.needsLineOfSight,
      castTime = dto.castTime,
      requiredLevel = dto.requiredLevel,
      description = dto.description,
      name = dto.name,
      archetype = dto.archetype,
      element = dto.element.name,
      power = dto.power,
      cooldown = dto.cooldown,
      statusEffect = dto.status?.name,
      yields = dto.yields.map { toYield(dto.identifier, it) }.toMutableList(),
      lines = dto.lines.toMutableList()
    )
  }

  /** Items are imported first, so an unknown one is a typo and fails the boot. */
  private fun toYield(skill: String, dto: SkillYmlDto.YieldDto): SkillYield {
    val item = itemRepository.findByIdentifier(dto.item)
      ?: throw IllegalArgumentException("Skill '$skill' yields unknown item '${dto.item}'")

    return SkillYield(itemId = item.id, chance = dto.chance)
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

  /**
   * Skills are content, so an edit to `skills.yml` has to reach an already-provisioned world -
   * zone-server runs `ddl-auto: update` and keeps its rows across restarts. The candidate entity
   * validates itself on construction, so an invalid edit fails the boot instead of being written.
   */
  override fun tryUpdate(dto: SkillYmlDto, entity: Skill): Boolean {
    return entity.updateContentFrom(newEntity(dto))
  }

  override fun postImport(entities: List<Skill>) {
    // TODO verify all skills which required a script actually have a script.
  }

  companion object {
    private const val SKILLS_RESOURCE = "skills.yml"

    private val LOG = KotlinLogging.logger { }
  }
}
