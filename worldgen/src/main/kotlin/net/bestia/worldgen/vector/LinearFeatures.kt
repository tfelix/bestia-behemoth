package net.bestia.worldgen.vector

/**
 * Factories for the standard linear features.
 *
 * They all follow the same three steps: resample the centerline to uniform station spacing, build
 * the station channels the profile needs as functions of arc length, and wrap the result in a
 * [PolylineFeature]. Doing it in one place keeps the invariant that stations are per vertex and
 * uniformly spaced - which is what [StationTable]'s spline interpolation assumes - from having to
 * be re-established by every producing stage.
 */
object LinearFeatures {

  /**
   * A river reach.
   *
   * Station spacing defaults to 75 m, roughly the middle of the 50-100 m the architecture calls for:
   * fine enough that a meander reads as a curve rather than a chain of straights, coarse enough that
   * a 50k-reach world stays in a few hundred megabytes.
   *
   * @param bedElevation bank-top elevation of the channel at arc length `s`
   * @param width wetted width at `s`, from hydraulic geometry (`w` grows roughly with `sqrt(Q)`)
   * @param depth channel depth below [bedElevation] at `s` (`d` grows roughly with `Q^0.4`)
   * @param shoulder floodplain half-width outside the channel, easing back to the surrounding
   *   terrain. Without it a big lowland river looks like a slot milled into a plain.
   */
  fun river(
    id: FeatureId,
    centerline: Polyline,
    stationSpacing: Double = 75.0,
    bedElevation: (s: Double) -> Double,
    width: (s: Double) -> Double,
    depth: (s: Double) -> Double,
    shoulder: (s: Double) -> Double = { width(it) }
  ): PolylineFeature {
    val line = centerline.resample(stationSpacing)
    val stations = StationTable.Builder(line.vertexCount)
      .channel(Profiles.CHANNEL_BED_ELEVATION) { bedElevation(line.arcLengthAt(it)) }
      .channel(Profiles.CHANNEL_WIDTH) { width(line.arcLengthAt(it)) }
      .channel(Profiles.CHANNEL_DEPTH) { depth(line.arcLengthAt(it)) }
      .channel(Profiles.CHANNEL_SHOULDER) { shoulder(line.arcLengthAt(it)) }
      .channel(PolylineFeature.CORRIDOR_CHANNEL) {
        val s = line.arcLengthAt(it)
        width(s) * 0.5 + shoulder(s)
      }
      .build()

    return PolylineFeature(
      id = id,
      kind = FeatureKind.RIVER_CHANNEL,
      centerline = line,
      stations = stations,
      profile = Profiles.riverChannel(stations),
      blend = BlendMode.MIN
    )
  }

  /**
   * A glacial trough, or - with the floor set below sea level and a rise in [floorElevation] at the
   * mouth - a fjord.
   *
   * @param halfWidthFloor half-width of the flat floor. This is the trait a 1 km raster cannot hold
   *   and the reason troughs are vector features at all.
   * @param wallExponent 2 gives the classic U; higher gives the near-vertical walls of a young trough
   */
  fun glacialTrough(
    id: FeatureId,
    centerline: Polyline,
    stationSpacing: Double = 100.0,
    kind: FeatureKind = FeatureKind.GLACIAL_TROUGH,
    floorElevation: (s: Double) -> Double,
    halfWidthFloor: (s: Double) -> Double,
    halfWidth: (s: Double) -> Double,
    wallHeight: (s: Double) -> Double,
    wallExponent: (s: Double) -> Double = { 2.0 }
  ): PolylineFeature {
    val line = centerline.resample(stationSpacing)
    val stations = StationTable.Builder(line.vertexCount)
      .channel(Profiles.CHANNEL_FLOOR_ELEVATION) { floorElevation(line.arcLengthAt(it)) }
      .channel(Profiles.CHANNEL_HALF_WIDTH_FLOOR) { halfWidthFloor(line.arcLengthAt(it)) }
      .channel(Profiles.CHANNEL_HALF_WIDTH) { halfWidth(line.arcLengthAt(it)) }
      .channel(Profiles.CHANNEL_WALL_HEIGHT) { wallHeight(line.arcLengthAt(it)) }
      .channel(Profiles.CHANNEL_WALL_EXPONENT) { wallExponent(line.arcLengthAt(it)) }
      .channel(PolylineFeature.CORRIDOR_CHANNEL) { halfWidth(line.arcLengthAt(it)) }
      .build()

    return PolylineFeature(
      id = id,
      kind = kind,
      centerline = line,
      stations = stations,
      profile = Profiles.glacialTrough(stations),
      blend = BlendMode.MIN
    )
  }

  /**
   * A road: a flat running surface cut and filled to grade, easing back to terrain over its
   * embankment.
   *
   * Roads reuse the machinery rivers already needed - the same centerline, stations and profile
   * evaluation, with a different cross-section. That is the payoff of pushing narrow linear features
   * into the vector tier rather than solving each one separately.
   *
   * **There is no road-junction smoothing, and the reason is the blend mode.** A `ROAD_JUNCTION` bowl was
   * planned as the twin of [FeatureKind.RIVER_CONFLUENCE], on the grounds that `min` of two profiles creases
   * along the bisector of a Y. That reasoning does not reach here: a road blends with [BlendMode.REPLACE], not
   * `MIN`, so two roads meeting do not `min` against each other at all - the higher-priority one simply wins its
   * columns outright. Whatever remains at a junction is a priority *step* between two overlapping carriageways,
   * which is a different defect with a different fix, and one nobody has measured. Measure it before building a
   * feature kind for it. `RIVER_CONFLUENCE` is not the precedent it looks like.
   */
  fun road(
    id: FeatureId,
    centerline: Polyline,
    stationSpacing: Double = 25.0,
    /** [FeatureKind.ROAD] between settlements, [FeatureKind.STREET] inside one. Same cross-section. */
    kind: FeatureKind = FeatureKind.ROAD,
    surfaceElevation: (s: Double) -> Double,
    halfWidth: (s: Double) -> Double,
    shoulder: (s: Double) -> Double = { halfWidth(it) * 3.0 },
    endTaper: Double = 0.0,
    /**
     * Set when [centerline] has already been resampled at [stationSpacing] and must be used as given.
     *
     * Resampling is not idempotent: it walks arc length and lays new vertices along the *chords* of the
     * old ones, so a second pass over an already-resampled line comes out a shade shorter than the first.
     * Harmless in itself - a metre and a half over thirty-six kilometres - and not harmless at all to a
     * caller that measured something against the line it passed in, because the feature then carries a
     * line that is not quite that one and any geometry derived from the first does not quite land on the
     * second. `SettlementStage` finds road-river crossings that way, and a road grazing the tip of a
     * river intersected one of the two lines and not the other, so the gap that should stop the
     * carriageway damming the channel was cut into a road that no longer had that crossing.
     */
    preResampled: Boolean = false
  ): PolylineFeature {
    val line = if (preResampled) centerline else centerline.resample(stationSpacing)
    val stations = StationTable.Builder(line.vertexCount)
      .channel(Profiles.CHANNEL_SURFACE_ELEVATION) { surfaceElevation(line.arcLengthAt(it)) }
      .channel(Profiles.CHANNEL_HALF_WIDTH) { halfWidth(line.arcLengthAt(it)) }
      .channel(Profiles.CHANNEL_SHOULDER) { shoulder(line.arcLengthAt(it)) }
      .channel(PolylineFeature.CORRIDOR_CHANNEL) {
        val s = line.arcLengthAt(it)
        halfWidth(s) + shoulder(s)
      }
      .build()

    return PolylineFeature(
      id = id,
      kind = kind,
      centerline = line,
      stations = stations,
      profile = Profiles.road(stations),
      endTaper = endTaper,
      blend = BlendMode.REPLACE
    )
  }

  /** A moraine ridge, piled on top of the terrain rather than cut into it. */
  fun moraine(
    id: FeatureId,
    centerline: Polyline,
    stationSpacing: Double = 50.0,
    halfWidth: (s: Double) -> Double,
    ridgeHeight: (s: Double) -> Double
  ): PolylineFeature {
    val line = centerline.resample(stationSpacing)
    val stations = StationTable.Builder(line.vertexCount)
      .channel(Profiles.CHANNEL_HALF_WIDTH) { halfWidth(line.arcLengthAt(it)) }
      .channel(Profiles.CHANNEL_RIDGE_HEIGHT) { ridgeHeight(line.arcLengthAt(it)) }
      .channel(PolylineFeature.CORRIDOR_CHANNEL) { halfWidth(line.arcLengthAt(it)) }
      .build()

    return PolylineFeature(
      id = id,
      kind = FeatureKind.MORAINE,
      centerline = line,
      stations = stations,
      profile = Profiles.moraine(stations),
      blend = BlendMode.ADD
    )
  }
}
