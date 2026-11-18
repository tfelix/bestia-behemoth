package net.bestia.zone.chronicle

/** Where an artifact of the chronicle is today. */
sealed interface ArtifactWhereabouts {

  /** A player's master took it. */
  data class WithMaster(val masterId: Long, val masterName: String) : ArtifactWhereabouts

  /** A standing settlement keeps it in its temple or treasury. */
  data class KeptAt(val settlement: Int) : ArtifactWhereabouts

  /** A living figure of the chronicle carries it. */
  data class WithFigure(val figure: Int) : ArtifactWhereabouts

  /** It lies in a site of the chronicle, such as a tomb or a battlefield. */
  data class AtSite(val site: Int) : ArtifactWhereabouts

  /** Lost without a trace. */
  data object Unknown : ArtifactWhereabouts
}
