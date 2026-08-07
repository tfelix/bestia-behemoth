package net.bestia.zone.world.prop

import net.bestia.worldgen.poi.PoiKind
import net.bestia.worldgen.voxel.PropKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `prop-kinds.yml` describes every kind that can reach a client.
 *
 * `PropKindRegistry.load` already refuses to start without one row per [StaticEntityKind], so this adds no rule -
 * it moves *when* the refusal happens. Without it the first sign of a kind added to the enum and forgotten in the
 * YAML is a server that will not boot, found by whoever next starts one; with it, it is a red test in the commit
 * that added the constant.
 *
 * `WorldObjectResidencyTest` cannot cover this: it stubs the registry deliberately, because what it is testing is
 * refcounting rather than configuration.
 */
class PropKindRegistryTest {

  @Test
  fun `every static entity kind is described`() {
    // The require inside load() is the assertion; this fails with its message rather than duplicating it.
    val registry = PropKindRegistry()
    registry.load()

    for (kind in StaticEntityKind.entries) {
      assertTrue(registry.of(kind).maxHp > 0, "$kind has no usable health")
      assertTrue(registry.of(kind).variants >= 1, "$kind has no mesh")
    }
  }

  /**
   * The presence of a `collect` block is the only rule for what a click may take, so this pins the four kinds
   * it is true for. A fifth appearing here without a matching `Collectible = true` row in the client's
   * `PropAppearance` is a drift the server answers with `COLLECT_NOT_COLLECTIBLE` rather than an item.
   */
  @Test
  fun `exactly the crystals and shards are collectible, and each names an item`() {
    val registry = PropKindRegistry().also { it.load() }

    val collectible = StaticEntityKind.entries.filter { registry.of(it).collect != null }

    assertEquals(
      setOf(
        StaticEntityKind.MANA_CRYSTAL_SMALL,
        StaticEntityKind.MANA_CRYSTAL_LARGE,
        StaticEntityKind.AETHERITE_SHARD_SMALL,
        StaticEntityKind.AETHERITE_SHARD_LARGE
      ),
      collectible.toSet()
    )

    for (kind in collectible) {
      val collect = registry.of(kind).collect!!
      assertTrue(collect.itemId > 0, "$kind yields no item")
      assertTrue(collect.amount > 0, "$kind yields nothing")

      // Terminal by design: a mined-out crystal does not grow back, so `shouldEmit` drops it forever.
      assertEquals(null, registry.of(kind).regrowSeconds, "$kind would regrow after being collected")
    }
  }

  /**
   * Every landmark in worldgen's catalogue reaches a distinct runtime kind.
   *
   * The join `StaticEntityKind.POI_KINDS` makes is by name and is checked at class load, so what this really
   * guards is the *other* direction: two catalogue entries mapping to one constant would be a name collision the
   * join cannot see, and would draw two different landmarks with one mesh.
   */
  @Test
  fun `each point of interest has its own runtime kind`() {
    val mapped = PoiKind.entries.map { StaticEntityKind.of(PropKind.POI, blighted = false, large = false, subKind = it.ordinal) }

    assertEquals(PoiKind.entries.size, mapped.toSet().size, "two landmarks share a runtime kind: $mapped")
    for ((poi, kind) in PoiKind.entries.zip(mapped)) {
      assertEquals("POI_${poi.name}", kind.name, "${poi.label} is mapped to the wrong runtime kind")
    }
  }
}
