package net.bestia.zone.world.spoor

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Runtime settings for reading tracks.
 *
 * The level gates are here rather than in `skills.yml` because they are numbers somebody will want to retune
 * against how useful the skill turns out to be, and none of them changes what is stored.
 *
 * @property signatureTtlSeconds Bestia seconds an [ActorSignature] is kept after the actor was last seen
 *   walking. Longer than a print's life on purpose: the print is what expires, and a signature outliving it
 *   only means the last print is still readable on the last second of its life.
 * @property maxSignatures actors remembered at once. Bounds the index on a busy shard.
 * @property refreshSeconds how often a walking actor's signature is rebuilt from its components. A track
 *   records what walked at the time, so a level-up reaching the index late costs nothing.
 * @property readRadiusPerLevelTiles how far around the read point each skill level searches.
 * @property headingLevel the level at which a reading says which way the tracks led.
 * @property identityLevel the level at which it says what made them.
 * @property nameLevel the level at which it names a player.
 * @property freshSeconds a track younger than this reads as fresh, and one older than [coldSeconds] as cold.
 */
@ConfigurationProperties(prefix = "spoor")
data class SpoorConfig(
  val signatureTtlSeconds: Long = 7_200,
  val maxSignatures: Int = 8_192,
  val refreshSeconds: Long = 300,
  val readRadiusPerLevelTiles: Long = 3,
  val headingLevel: Int = 2,
  val identityLevel: Int = 3,
  val nameLevel: Int = 4,
  val freshSeconds: Long = 300,
  val coldSeconds: Long = 2_400,
) {

  init {
    require(signatureTtlSeconds > 0) { "signatureTtlSeconds must be positive, was $signatureTtlSeconds" }
    require(maxSignatures > 0) { "maxSignatures must be positive, was $maxSignatures" }
    require(refreshSeconds > 0) { "refreshSeconds must be positive, was $refreshSeconds" }
    require(readRadiusPerLevelTiles > 0) {
      "readRadiusPerLevelTiles must be positive, was $readRadiusPerLevelTiles"
    }
    require(freshSeconds in 1 until coldSeconds) {
      "freshSeconds must be positive and below coldSeconds, was $freshSeconds against $coldSeconds"
    }
  }
}
