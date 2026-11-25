package net.bestia.worldgen.place

/**
 * How the provinces are shaped. Like [RegionParams] this is not part of `pipelineVersion`: provinces are
 * built after the pipeline, and a change here moves [Provinces.partitionVersion] instead.
 */
data class ProvinceParams(

  /**
   * How far land may lie from every town before the wilds get a province of their own, in metres. A bit
   * more than the spacing of towns, so towns hold most of the land and only true wilds are left over.
   */
  val wildGap: Double = 30_000.0,
) {

  init {
    require(wildGap > 0.0) { "wildGap must be positive, was $wildGap" }
  }
}
