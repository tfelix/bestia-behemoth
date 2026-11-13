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

  /** Takes the whole catalogue. Identifiers no [SkillId] names are content only, so they are dropped. */
  fun load(idByIdentifier: Map<String, Long>) {
    ids = SkillId.entries
      .mapNotNull { skillId -> idByIdentifier[skillId.name]?.let { skillId to it } }
      .toMap(EnumMap(SkillId::class.java))
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
