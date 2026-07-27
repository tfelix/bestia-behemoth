package net.bestia.worldgen.core

/**
 * Everything a stage is allowed to see while it runs.
 *
 * Note what is missing: no clock, no filesystem, no network, no shared RNG, no reference to other
 * stages. A stage can only reach its declared upstream layers and features, its config, and RNG
 * streams derived from its own identity. That is what makes `generate` a pure function of
 * `(seed, region, upstream)` in practice and not just in intent.
 */
class GenContext internal constructor(
  private val stage: Stage,
  val config: WorldConfig,
  val layers: ScopedLayerStore,
  val features: ScopedFeatureStore
) {

  val seed: Long get() = config.seed

  /**
   * A deterministic RNG stream for this stage at the given coordinate.
   *
   * The stream depends on the world seed, this stage's id and version, and the coordinate - and on
   * nothing else. Two nodes generating the same coordinate get the same stream; generating a
   * neighbour first changes nothing.
   */
  fun rng(vararg coordinates: Long): GenRng =
    GenRng.derive(config.seed, stage.id, stage.version, *coordinates)

  fun rng(region: CellRegion): GenRng =
    rng(region.minX.toLong(), region.minY.toLong(), region.width.toLong(), region.height.toLong())

  fun rng(chunk: ChunkPos): GenRng = rng(chunk.key())

  /** A single deterministic value for a cell, without allocating a stream per cell. */
  fun cellHash(x: Int, y: Int, salt: Long = 0L): Long =
    GenRng.hash(config.seed, stage.id.hash, stage.version.toLong(), x.toLong(), y.toLong(), salt)

  /** [cellHash] mapped into `[0, 1)`. */
  fun cellUnit(x: Int, y: Int, salt: Long = 0L): Double =
    (cellHash(x, y, salt) ushr 11) * (1.0 / (1L shl 53))

  /** Allocates a [FloatLayer] sized for [region]; the usual first line of a raster stage. */
  fun floatLayer(id: LayerId, region: CellRegion, initial: Float = 0f) =
    FloatLayer.filled(id, region, initial)

  fun intLayer(id: LayerId, region: CellRegion, initial: Int = 0) =
    IntLayer.filled(id, region, initial)
}
