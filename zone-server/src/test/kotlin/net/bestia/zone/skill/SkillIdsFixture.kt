package net.bestia.zone.skill

/** A [SkillIds] loaded as if the skill import had run, keyed by identifier like `skills.yml`. */
fun skillIdsOf(idByIdentifier: Map<String, Long>): SkillIds {
  return SkillIds().apply { load(idByIdentifier) }
}

fun skillIdsOf(vararg ids: Pair<SkillId, Long>): SkillIds {
  return skillIdsOf(ids.associate { (skillId, id) -> skillId.name to id })
}
