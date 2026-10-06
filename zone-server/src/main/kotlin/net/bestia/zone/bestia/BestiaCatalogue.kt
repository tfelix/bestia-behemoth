package net.bestia.zone.bestia

import org.springframework.stereotype.Service

/**
 * The species catalogue, read once instead of per creature.
 *
 * ### Why this exists
 *
 * [net.bestia.zone.spawn.BestiaEntitySpawner] used to call [BestiaRepository.findByIdOrThrow] on every single spawn, and
 * `BestiaRepository` is a plain `JpaRepository` with no cache - so **every creature entering the world cost a
 * synchronous SELECT on the tick thread, inside the world lock**. At one den restocking one creature per
 * quarter second that was survivable, which is why it went unnoticed. It stops being survivable the moment
 * anything spawns creatures in bulk.
 *
 * ### Why a plain map is enough
 *
 * The catalogue is immutable for the life of the process: `MobImporterBootRunner` writes it at `@Order(102)`
 * and nothing edits a `bestia` row afterwards. So there is no invalidation to get wrong, and none of the
 * per-entry expiry a cache would bring.
 *
 * Loaded lazily rather than from a boot runner, the way `WildSpawnerService.dens` is: the first reader is
 * `WildSpawnerBootRunner` at `@Order(105)`, comfortably after the importer and strictly before the tick loop
 * starts. A caller that somehow arrives earlier gets an honest empty catalogue and a
 * [BestiaNotFoundException], not a silently wrong answer.
 *
 * ### Holding detached entities is safe here, and would not always be
 *
 * These [Bestia] instances outlive their session, so touching a lazy `@OneToMany` on one throws. That is
 * fine because nothing on any runtime path does: loot goes through `MobLootSpawner`'s own
 * drop table, and the learnset through [learnset]. **A new field that is a collection must not be
 * read through this catalogue.**
 */
@Service
class BestiaCatalogue(
  private val bestiaRepository: BestiaRepository,
  private val bestiaSkillRepository: BestiaSkillRepository,
) {

  /**
   * Sorted by id, because `WildSpawnerService` draws a species from this list by index against a seeded
   * hash - an unstable order would hand the same den a different species on the next boot.
   */
  private val ordered: List<Bestia> by lazy {
    bestiaRepository.findAll().sortedBy { it.id }
  }

  private val byId: Map<Long, Bestia> by lazy {
    ordered.associateBy { it.id }
  }

  private val byIdentifier: Map<String, Bestia> by lazy {
    ordered.associateBy { it.identifier }
  }

  fun all(): List<Bestia> {
    return ordered
  }

  fun byId(id: Long): Bestia {
    return byId[id] ?: throw BestiaNotFoundException(id)
  }

  /** Like [byId], for a caller that has its own answer for an unknown species. */
  fun find(id: Long): Bestia? {
    return byId[id]
  }

  // Loaded as plain values, because the cached species cannot load its own `skills`.
  private val learnsets: Map<Long, List<LearnedSkill>> by lazy {
    bestiaSkillRepository.findAllLearned().groupBy { it.bestiaId }
  }

  fun learnset(bestiaId: Long): List<LearnedSkill> {
    return learnsets[bestiaId] ?: emptyList()
  }

  fun byIdentifier(identifier: String): Bestia {
    return byIdentifier[identifier] ?: throw BestiaNotFoundException(identifier)
  }
}
