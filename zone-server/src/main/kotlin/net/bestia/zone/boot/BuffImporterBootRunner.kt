package net.bestia.zone.boot

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.buff.StatusEffectDefinition
import net.bestia.zone.battle.buff.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.buff.StatusEffect
import net.bestia.zone.battle.buff.StatusEffectSource
import net.bestia.zone.battle.buff.StatusEffectTriggerAction
import net.bestia.zone.battle.buff.StatusEffectTriggerEvent
import net.bestia.zone.battle.buff.ModifierMode
import net.bestia.zone.battle.buff.StackBehavior
import net.bestia.zone.battle.status.StatType
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
      val effectSource: StatusEffectSource,
      val showIcon: Boolean,
      val baseDurationSeconds: Double,
      val durationPerLevel: Double = 0.0,
      val stackBehavior: StackBehavior = StackBehavior.REFRESH_DURATION,
      val effects: List<EffectDto> = emptyList()
    )

    data class EffectDto(
      val type: String,
      // STAT_MODIFIER fields
      val stat: StatType? = null,
      val mode: ModifierMode? = null,
      val valuePerLevel: Double? = null,
      // TRIGGER fields
      val on: StatusEffectTriggerEvent? = null,
      val action: String? = null,
      val percent: Double? = null,
      val consumeOnTrigger: Boolean = true
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
      polarity = dto.effectSource,
      showIcon = dto.showIcon,
      baseDurationSeconds = dto.baseDurationSeconds,
      durationPerLevel = dto.durationPerLevel,
      stackBehavior = dto.stackBehavior,
      effects = dto.effects.map { toEffect(it) }
    )
  }

  private fun toEffect(dto: StatusEffectYmlDto.EffectDto): StatusEffect {
    return when (dto.type) {
      "STAT_MODIFIER" -> StatusEffect.StatModifierEffect(
        stat = dto.stat ?: error("STAT_MODIFIER effect missing 'stat'"),
        mode = dto.mode ?: error("STAT_MODIFIER effect missing 'mode'"),
        valuePerLevel = dto.valuePerLevel ?: error("STAT_MODIFIER effect missing 'valuePerLevel'")
      )
      "TRIGGER" -> StatusEffect.TriggerEffect(
        on = dto.on ?: error("TRIGGER effect missing 'on'"),
        action = toTriggerAction(dto),
        consumeOnTrigger = dto.consumeOnTrigger
      )
      else -> error("Unknown status effect type '${dto.type}'")
    }
  }

  private fun toTriggerAction(dto: StatusEffectYmlDto.EffectDto): StatusEffectTriggerAction {
    return when (dto.action) {
      "REFLECT_DAMAGE" -> StatusEffectTriggerAction.ReflectDamage(
        percent = dto.percent ?: error("REFLECT_DAMAGE action missing 'percent'")
      )
      else -> error("Unknown status effect trigger action '${dto.action}'")
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
