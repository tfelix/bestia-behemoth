package net.bestia.zone.crafting.discovery

import org.springframework.stereotype.Service

/**
 * Where each material sits in the crafting feature space, from `craft-axes` in `items.yml`. An item without them
 * cannot be used to discover anything. Loaded once at boot by the item importer and only read afterwards.
 */
@Service
class MaterialAxes {

  private var byItemId: Map<Long, CraftVector> = emptyMap()

  fun load(axes: Map<Long, CraftVector>) {
    byItemId = axes
  }

  fun of(itemId: Long): CraftVector? = byItemId[itemId]

  fun all(): Map<Long, CraftVector> = byItemId
}
