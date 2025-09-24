package net.bestia.zone.boot

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.attack.Attack
import net.bestia.zone.battle.attack.AttackRepository
import net.bestia.zone.battle.attack.AttackType
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Imports the items from the YML resources into the database.
 */
@Component
@Order(102)
class AttackImporterBootRunner(
  attackRepository: AttackRepository,
) : CommandLineRunner,
  YmlImporterBootRunner<AttackImporterBootRunner.AttackYmlDto, Attack>(
    "Attack",
    "attack",
    attackRepository,
    AttackYmlDto::class.java
  ) {
  data class AttackYmlDto(
    val identifier: String,
    val strength: Int?,
    val manaCost: Int,
    val type: AttackType,
    val script: String?,
    val range: Int?,
    val needsLineOfSight: Boolean
  )

  override fun newEntity(dto: AttackYmlDto): Attack {

    return Attack(
      identifier = dto.identifier,
      strength = dto.strength,
      type = dto.type,
      script = dto.script,
      manaCost = dto.manaCost,
      range = dto.range,
      needsLineOfSight = dto.needsLineOfSight
    )
  }

  override fun getEntityIdentifier(entity: Attack): String {
    return entity.identifier
  }

  override fun getYmlIdentifier(dto: AttackYmlDto): String {
    return dto.identifier
  }

  override fun tryUpdate(dto: AttackYmlDto, entity: Attack): Boolean {
    // TODO no sure if we should even support updates
    return false
  }

  override fun postImport(entities: List<Attack>) {
    // TODO verify all attacks which required a script actually have a script.
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}