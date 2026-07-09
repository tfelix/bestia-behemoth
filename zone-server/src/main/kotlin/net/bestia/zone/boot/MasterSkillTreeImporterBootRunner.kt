package net.bestia.zone.boot

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.skill.MasterSkillPrerequisite
import net.bestia.zone.battle.skill.MasterSkillTreeNode
import net.bestia.zone.battle.skill.MasterSkillTreeRegistry
import net.bestia.zone.battle.skill.SkillRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/**
 * Loads the master skill tree (nodes + prerequisite DAG) from `master_skill_tree.yml` into
 * [MasterSkillTreeRegistry]. This is config, not player state, so it lives entirely in memory and
 * is never persisted to the database. Ordered after [SkillImporterBootRunner] (102) since it
 * resolves skill references by identifier against already-imported `Skill` rows.
 */
@Component
@Order(103)
class MasterSkillTreeImporterBootRunner(
  private val skillRepository: SkillRepository,
  private val masterSkillTreeRegistry: MasterSkillTreeRegistry
) : CommandLineRunner {

  data class MasterSkillTreeYmlDto(
    val skills: List<NodeDto>
  ) {
    data class NodeDto(
      val skill: String,
      val maxLevel: Int,
      val prerequisites: List<PrerequisiteDto> = emptyList()
    )

    data class PrerequisiteDto(
      val skill: String,
      val level: Int
    )
  }

  override fun run(vararg args: String?) {
    val objectMapper = ObjectMapper(YAMLFactory()).apply {
      registerKotlinModule()
    }

    val resource = ClassPathResource("master_skill_tree.yml")
    val dto = resource.inputStream.use { objectMapper.readValue(it, MasterSkillTreeYmlDto::class.java) }

    val nodes = dto.skills.map { toNode(it) }
    masterSkillTreeRegistry.load(nodes)

    LOG.info { "Master skill tree loaded: ${nodes.size} nodes" }
  }

  private fun toNode(dto: MasterSkillTreeYmlDto.NodeDto): MasterSkillTreeNode {
    val skill = skillRepository.findByIdentifier(dto.skill)
      ?: error("Master skill tree references unknown skill '${dto.skill}'")

    val prerequisites = dto.prerequisites.map { prerequisite ->
      val prerequisiteSkill = skillRepository.findByIdentifier(prerequisite.skill)
        ?: error("Master skill tree node '${dto.skill}' references unknown prerequisite skill '${prerequisite.skill}'")

      MasterSkillPrerequisite(
        prerequisiteSkillId = prerequisiteSkill.id,
        requiredLevel = prerequisite.level
      )
    }

    return MasterSkillTreeNode(
      skillId = skill.id,
      maxLevel = dto.maxLevel,
      prerequisites = prerequisites
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
