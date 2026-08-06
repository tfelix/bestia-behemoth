package net.bestia.zone.boot

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.dialog.DialogDefinition
import net.bestia.zone.dialog.DialogDefinitionRegistry
import net.bestia.zone.dialog.DialogType
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/**
 * Loads the dialog catalog from `dialogs.yml` into [DialogDefinitionRegistry]. Config, not player
 * state, so it lives entirely in memory and is never persisted - same shape as
 * [StatusEffectImporterBootRunner], and ordered right after it.
 */
@Component
@Order(105)
class DialogImporterBootRunner(
  private val dialogDefinitionRegistry: DialogDefinitionRegistry
) : CommandLineRunner {

  data class DialogYmlDto(
    val dialogs: List<DialogDto>
  ) {
    data class DialogDto(
      val id: Int,
      val identifier: String,
      val type: DialogType = DialogType.CONFIRM,
      val args: List<String> = emptyList()
    )
  }

  override fun run(vararg args: String?) {
    val objectMapper = ObjectMapper(YAMLFactory()).apply {
      registerKotlinModule()
    }

    val resource = ClassPathResource("dialogs.yml")
    val dto = resource.inputStream.use { objectMapper.readValue(it, DialogYmlDto::class.java) }

    val definitions = dto.dialogs.map { toDefinition(it) }
    dialogDefinitionRegistry.load(definitions)

    LOG.info { "Dialog catalog loaded: ${definitions.size} definitions" }
  }

  private fun toDefinition(dto: DialogYmlDto.DialogDto): DialogDefinition {
    return DialogDefinition(
      id = dto.id,
      identifier = dto.identifier,
      type = dto.type,
      args = dto.args
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
