package net.bestia.zone.boot

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.status.StatusEffectDefinition
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/**
 * Loads the status effect catalog from `status_effects.yml` into [StatusEffectDefinitionRegistry]. This is config,
 * not player state, so it lives entirely in memory and is never persisted to the database - same
 * shape as [MasterSkillTreeImporterBootRunner]. Ordered after the skill importers since nothing
 * here references them, but status effects are conceptually "skill-adjacent" content.
 */
@Component
@Order(104)
class StatusEffectImporterBootRunner(
  private val statusEffectDefinitionRegistry: StatusEffectDefinitionRegistry
) : CommandLineRunner {

  data class StatusEffectYmlDto(
    val statusEffects: List<StatusEffectDto>
  ) {
    data class StatusEffectDto(
      val id: Long,
      val identifier: String,
      val isSyncedToClient: Boolean = true,
      val script: String,
      // Not read into StatusEffectDefinition - zone-server has no runtime use for buff/debuff
      // polarity or icon visibility, but these stay parseable here for a possible future
      // Godot-resource generation step (same relationship skills.yml has to the client Attack DB).
      val polarity: String? = null,
      val showIcon: Boolean = true
    )
  }

  override fun run(vararg args: String?) {
    val objectMapper = ObjectMapper(YAMLFactory()).apply {
      registerKotlinModule()
    }

    val resource = ClassPathResource("status_effects.yml")
    val dto = resource.inputStream.use { objectMapper.readValue(it, StatusEffectYmlDto::class.java) }

    val definitions = dto.statusEffects.map { toDefinition(it) }
    statusEffectDefinitionRegistry.load(definitions)

    LOG.info { "Status effect catalog loaded: ${definitions.size} definitions" }
  }

  private fun toDefinition(dto: StatusEffectYmlDto.StatusEffectDto): StatusEffectDefinition {
    return StatusEffectDefinition(
      id = dto.id,
      identifier = dto.identifier,
      isSyncedToClient = dto.isSyncedToClient,
      script = dto.script
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
