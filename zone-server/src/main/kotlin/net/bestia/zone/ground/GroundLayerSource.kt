package net.bestia.zone.ground

/**
 * Where one ground layer's cells come from.
 *
 * Exists so [GroundOverlayService] never learns what a layer *means*. The fire owns its scars and the wear
 * registry owns its paths; both hand over a column's cells in wire form, each in the encoding that suits it,
 * and neither knows the other exists. Adding a layer is then a bean, not an edit to the broadcaster.
 */
interface GroundLayerSource {

  val layer: GroundLayer

  /**
   * @return this column's entry for [layer], empty when the layer has nothing here - which is the common
   *   answer for almost every layer on almost every column, and what retires the layer when it is sent
   */
  fun entryAt(columnKey: Long): GroundLayerEntry
}
