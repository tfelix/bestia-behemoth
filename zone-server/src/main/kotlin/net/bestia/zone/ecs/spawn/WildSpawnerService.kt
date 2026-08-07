package net.bestia.zone.ecs.spawn

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.mana.CorruptionStage
import net.bestia.worldgen.spawn.SpawnerChannels
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.zone.bestia.Bestia
import net.bestia.zone.bestia.BestiaRepository
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.stream.ChunkCoords
import org.springframework.stereotype.Service

/**
 * Turns the world's `BESTIA_SPAWN` markers into [Spawner] components, and decides which species each holds.
 *
 * ### Why there is no table here
 *
 * `MasterSpawnPointService` caches its equally-pure answer in `master_spawn_point`, and it is right to: an
 * account row references a chosen spawn point by identity, so that identity has to outlive the process.
 * A den references nothing and nothing references it - the markers are a pure function of the world seed and
 * the world tier is regenerated at boot in about half a second. Storing them would be a table that can go
 * stale against the generator that produced it, in exchange for nothing.
 *
 * ### Species selection
 *
 * A den says how hard it is and what kind of country it is in; the *catalogue* says what lives in country like
 * that. The join is:
 *
 * - the species' `level` must fall inside the den's `LEVEL_MIN..LEVEL_MAX`;
 * - its `habitat`, if it names any biomes, must include the den's own;
 * - `corruptedOnly` species only in dens standing on corrupted ground, and never elsewhere;
 * - `boss` species only in dens flagged `BOSS`, and a boss den takes nothing else.
 *
 * Among what survives, one is drawn by `spawnWeight`. The draw is **seeded from the world seed and the den's
 * feature id**, not from a clock or a shared RNG, so the same den holds the same species on every boot of the
 * same world - which is what stops a server restart shuffling the whole bestiary.
 *
 * A den that nothing fits is dropped and counted. That count is the honest measure of whether the catalogue
 * covers the level ramp the generator produced, and it is logged at boot for exactly that reason: two mobs
 * cannot fill a 1-to-100 curve, and the log is where that becomes visible instead of being discovered as an
 * empty wilderness.
 */
@Service
class WildSpawnerService(
  private val worldService: WorldService,
  private val bestiaRepository: BestiaRepository
) {

  /** One den ready to be placed into the ECS world. */
  data class Den(
    val bestiaId: Long,
    val position: Vec3L,
    val pack: Int,
    val range: Int
  )

  /**
   * Every den on the world, with a species chosen for each.
   *
   * Computed once and memoised for the life of the process - a pure function of the world, so it cannot go
   * stale, and `ChunkService.loaded by lazy` is the precedent for holding one in a field.
   */
  val dens: List<Den> by lazy { resolve() }

  private fun resolve(): List<Den> {
    val generated = worldService.generated
    val config = generated.config
    val catalogue = bestiaRepository.findAll().toList()

    if (catalogue.isEmpty()) {
      LOG.warn { "No bestia in the catalogue; every den on the world will stay empty" }
      return emptyList()
    }

    val out = mutableListOf<Den>()
    var unfilled = 0
    var markers = 0

    for (feature in generated.world.features.all()) {
      if (feature.kind != FeatureKind.BESTIA_SPAWN) continue
      val marker = feature as? PointMarker ?: continue
      markers++

      val levelMin = marker.attribute(SpawnerChannels.LEVEL_MIN).toInt()
      val levelMax = marker.attribute(SpawnerChannels.LEVEL_MAX).toInt()
      val biome = Biome.entries[marker.attribute(SpawnerChannels.BIOME).toInt()]
      val corrupted = marker.attribute(SpawnerChannels.CORRUPTION) >= CorruptionStage.CORRUPTED
      val boss = marker.attribute(SpawnerChannels.BOSS) >= 0.5
      val pack = marker.attribute(SpawnerChannels.PACK).toInt().coerceAtLeast(1)
      val radius = marker.attribute(SpawnerChannels.RADIUS)

      val species = pick(
        catalogue, worldService.record.seed, marker.id.value, levelMin, levelMax, biome, corrupted, boss
      )
      if (species == null) {
        unfilled++
        continue
      }

      // Voxel indices, not metres. `ChunkCoords.VOXELS_PER_POSITION_UNIT` is 1 and `voxelSize` is 1 m, so the
      // two happen to coincide today - going through the conversion anyway is what keeps this correct if
      // either ever moves.
      val height = generated.base.heightAt(marker.position.x, marker.position.y)
      val position = Vec3L(
        x = (marker.position.x / config.voxelSize).toLong(),
        y = (marker.position.y / config.voxelSize).toLong(),
        z = ChunkCoords.standingZ(config, height)
      )

      out.add(
        Den(
          bestiaId = species.id,
          position = position,
          pack = if (species.boss) 1 else pack,
          range = radius.toInt().coerceAtLeast(1)
        )
      )
    }

    LOG.info {
      "Wild dens: $markers markers, ${out.size} stocked, $unfilled with no species in the catalogue " +
          "(levels ${catalogue.minOf { it.level }}..${catalogue.maxOf { it.level }} available)"
    }

    return out
  }

  /**
   * The species join, and the weighted draw over what it leaves.
   *
   * In a companion and `internal` rather than private on the instance, deliberately: this is the only part of
   * this class with a decision in it, and everything else is reading a feature store and a repository. Kept
   * here it can be tested against hand-built species with no Spring context, no database and no generated
   * world - which is what makes it worth testing at all.
   */
  internal companion object {
    private val LOG = KotlinLogging.logger { }

    /**
     * Draws a species for one den, deterministically.
     *
     * The stream is `hash(worldSeed, featureId)` rather than a shared generator, so which creature lives in
     * which den is a property of the world rather than of the order dens happened to be resolved in - and a
     * server restart does not shuffle the bestiary.
     */
    internal fun pick(
      catalogue: List<Bestia>,
      worldSeed: Long,
      featureId: Long,
      levelMin: Int,
      levelMax: Int,
      biome: Biome,
      corrupted: Boolean,
      boss: Boolean
    ): Bestia? {
      val eligible = catalogue.filter { fits(it, levelMin, levelMax, biome, corrupted, boss) }
      if (eligible.isEmpty()) return null

      val total = eligible.sumOf { it.spawnWeight.coerceAtLeast(1) }
      var roll = (GenRng.hashUnit(worldSeed, featureId) * total).toInt().coerceIn(0, total - 1)

      for (candidate in eligible) {
        roll -= candidate.spawnWeight.coerceAtLeast(1)
        if (roll < 0) return candidate
      }
      return eligible.last()
    }

    internal fun fits(
      species: Bestia,
      levelMin: Int,
      levelMax: Int,
      biome: Biome,
      corrupted: Boolean,
      boss: Boolean
    ): Boolean {
      // Both ways round. An ordinary den must not hold a boss either, or the one-per-province rarity the
      // generator arranged means nothing.
      if (boss != species.boss) return false
      if (species.level !in levelMin..levelMax) return false
      if (species.corruptedOnly && !corrupted) return false

      if (species.habitat.isNotEmpty()) {
        val allowed = species.habitat.split(',')
        if (biome.name !in allowed) return false
      }

      return true
    }
  }
}
