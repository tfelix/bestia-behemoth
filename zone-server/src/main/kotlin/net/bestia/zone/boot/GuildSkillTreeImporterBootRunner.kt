package net.bestia.zone.boot

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.guild.skill.GuildSkillNode
import net.bestia.zone.guild.skill.GuildSkillTree
import net.bestia.zone.skill.persistence.SkillRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/** Loads `guild_skill_tree.yml` into [GuildSkillTree], after [SkillImporterBootRunner] wrote the skill rows. */
@Component
@Order(104)
class GuildSkillTreeImporterBootRunner(
  private val skillRepository: SkillRepository,
  private val guildSkillTree: GuildSkillTree,
) : CommandLineRunner {

  data class GuildSkillTreeYmlDto(
    val skills: List<NodeDto>
  ) {
    data class NodeDto(
      val skill: String,
      val maxLevel: Int,
      val prerequisites: List<PrerequisiteDto> = emptyList(),
    )

    data class PrerequisiteDto(
      val skill: String,
      val level: Int,
    )
  }

  override fun run(vararg args: String?) {
    val objectMapper = ObjectMapper(YAMLFactory()).apply {
      registerKotlinModule()
    }

    val dto = ClassPathResource(FILE).inputStream.use {
      objectMapper.readValue(it, GuildSkillTreeYmlDto::class.java)
    }

    val nodes = dto.skills.map { toNode(it) }
    guildSkillTree.load(nodes)

    LOG.info { "Guild skill tree loaded: ${nodes.size} nodes" }
  }

  private fun toNode(dto: GuildSkillTreeYmlDto.NodeDto): GuildSkillNode {
    val prerequisites = dto.prerequisites.map { prerequisite ->
      GuildSkillNode.Prerequisite(skillId = skillIdOf(prerequisite.skill), level = prerequisite.level)
    }

    return GuildSkillNode(
      skillId = skillIdOf(dto.skill),
      identifier = dto.skill,
      maxLevel = dto.maxLevel,
      prerequisites = prerequisites,
    )
  }

  private fun skillIdOf(identifier: String): Long {
    val skill = skillRepository.findByIdentifier(identifier)
      ?: error("$FILE references unknown skill '$identifier'")

    return skill.id
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    private const val FILE = "guild_skill_tree.yml"
  }
}
