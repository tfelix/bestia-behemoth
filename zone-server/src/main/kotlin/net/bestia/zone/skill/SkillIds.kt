package net.bestia.zone.skill

import org.springframework.stereotype.Component
import java.util.EnumMap

/**
 * The catalogue id of every [SkillId], held in memory so the tick can name a skill without SQL.
 * [net.bestia.zone.boot.SkillIdsBootRunner] fills it right after the skill import.
 */
@Component
class SkillIds {

  @Volatile
  private var ids: Map<SkillId, Long>? = null

  @Volatile
  private var byIdentifier: Map<String, Long> = emptyMap()

  /** Takes the whole catalogue. */
  fun load(idByIdentifier: Map<String, Long>) {
    ids = SkillId.entries
      .mapNotNull { skillId -> idByIdentifier[skillId.name]?.let { skillId to it } }
      .toMap(EnumMap(SkillId::class.java))
    byIdentifier = idByIdentifier
  }

  /** The id of any skill by its `skills.yml` identifier, such as one a species' essence names; null if unknown. */
  fun idOf(identifier: String): Long? {
    return byIdentifier[identifier]
  }

  /**
   * Null when `skills.yml` has no such skill. [SkillCatalogBootValidator] fails the boot on that, so the
   * call sites' fallbacks are only a second line.
   */
  operator fun get(skillId: SkillId): Long? {
    val loaded = checkNotNull(ids) { "SkillIds was read before the skill import loaded it" }
    return loaded[skillId]
  }
}
