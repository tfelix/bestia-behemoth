package net.bestia.zone.boot

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.buff.BuffDefinition
import net.bestia.zone.battle.buff.BuffDefinitionRegistry
import net.bestia.zone.battle.buff.BuffEffect
import net.bestia.zone.battle.buff.BuffPolarity
import net.bestia.zone.battle.buff.BuffTriggerAction
import net.bestia.zone.battle.buff.BuffTriggerEvent
import net.bestia.zone.battle.buff.ModifierMode
import net.bestia.zone.battle.buff.StackBehavior
import net.bestia.zone.battle.status.StatType
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/**
 * Loads the buff/debuff catalog from `buffs.yml` into [BuffDefinitionRegistry]. This is config,
 * not player state, so it lives entirely in memory and is never persisted to the database - same
 * shape as [MasterSkillTreeImporterBootRunner]. Ordered after the skill importers since nothing
 * here references them, but buffs are conceptually "skill-adjacent" content.
 */
@Component
@Order(104)
class BuffImporterBootRunner(
  private val buffDefinitionRegistry: BuffDefinitionRegistry
) : CommandLineRunner {

  data class BuffYmlDto(
    val buffs: List<BuffDto>
  ) {
    data class BuffDto(
      val id: Long,
      val identifier: String,
      val polarity: BuffPolarity,
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
      val on: BuffTriggerEvent? = null,
      val action: String? = null,
      val percent: Double? = null,
      val consumeOnTrigger: Boolean = true
    )
  }

  override fun run(vararg args: String?) {
    val objectMapper = ObjectMapper(YAMLFactory()).apply {
      registerKotlinModule()
    }

    val resource = ClassPathResource("buffs.yml")
    val dto = resource.inputStream.use { objectMapper.readValue(it, BuffYmlDto::class.java) }

    val definitions = dto.buffs.map { toDefinition(it) }
    buffDefinitionRegistry.load(definitions)

    LOG.info { "Buff catalog loaded: ${definitions.size} definitions" }
  }

  private fun toDefinition(dto: BuffYmlDto.BuffDto): BuffDefinition {
    return BuffDefinition(
      id = dto.id,
      identifier = dto.identifier,
      polarity = dto.polarity,
      showIcon = dto.showIcon,
      baseDurationSeconds = dto.baseDurationSeconds,
      durationPerLevel = dto.durationPerLevel,
      stackBehavior = dto.stackBehavior,
      effects = dto.effects.map { toEffect(it) }
    )
  }

  private fun toEffect(dto: BuffYmlDto.EffectDto): BuffEffect {
    return when (dto.type) {
      "STAT_MODIFIER" -> BuffEffect.StatModifierEffect(
        stat = dto.stat ?: error("STAT_MODIFIER effect missing 'stat'"),
        mode = dto.mode ?: error("STAT_MODIFIER effect missing 'mode'"),
        valuePerLevel = dto.valuePerLevel ?: error("STAT_MODIFIER effect missing 'valuePerLevel'")
      )
      "TRIGGER" -> BuffEffect.TriggerEffect(
        on = dto.on ?: error("TRIGGER effect missing 'on'"),
        action = toTriggerAction(dto),
        consumeOnTrigger = dto.consumeOnTrigger
      )
      else -> error("Unknown buff effect type '${dto.type}'")
    }
  }

  private fun toTriggerAction(dto: BuffYmlDto.EffectDto): BuffTriggerAction {
    return when (dto.action) {
      "REFLECT_DAMAGE" -> BuffTriggerAction.ReflectDamage(
        percent = dto.percent ?: error("REFLECT_DAMAGE action missing 'percent'")
      )
      else -> error("Unknown buff trigger action '${dto.action}'")
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
