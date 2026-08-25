package net.bestia.zone.world.prop

import net.bestia.worldgen.poi.PoiKind
import net.bestia.worldgen.voxel.PropKind
import net.bestia.zone.world.fire.GroundFireConfig
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
   * The presence of a `collect` block is the only rule for what a click may take, so this pins the kinds it is
   * true for. One appearing here without a matching `Collectible = true` row in the client's `PropAppearance`
   * is a drift the server answers with `COLLECT_NOT_COLLECTIBLE` rather than an item.
   */
  @Test
  fun `exactly the minerals and the plants are collectible, and each names an item`() {
    val registry = PropKindRegistry().also { it.load() }

    val collectible = StaticEntityKind.entries.filter { registry.of(it).collect != null }

    assertEquals(MINERALS + PLANTS, collectible.toSet())

    for (kind in collectible) {
      val collect = registry.of(kind).collect!!
      assertTrue(collect.itemId > 0, "$kind yields no item")
      assertTrue(collect.amount > 0, "$kind yields nothing")
    }
  }

  /**
   * A picked mineral is gone and a picked plant comes back.
   *
   * The distinction the two halves of `collect` have to keep, and it is the reason this is not one assertion
   * over every collectible kind. A mined-out crystal is terminal - `shouldEmit` drops it for good - while a
   * herb regrows, which is the first thing in the world that is both picked *and* temporary. Getting it the
   * wrong way round is invisible: an infinitely re-collectible crystal and a herb that never returns both look
   * entirely healthy in the moment they are collected.
   */
  @Test
  fun `minerals do not come back and plants do`() {
    val registry = PropKindRegistry().also { it.load() }

    for (kind in MINERALS) {
      assertEquals(null, registry.of(kind).regrowSeconds, "$kind would regrow after being mined out")
    }

    for (kind in PLANTS) {
      val regrow = registry.of(kind).regrowSeconds
      assertTrue(regrow != null && regrow > 0, "$kind is picked once and never comes back")
    }
  }

  /**
   * A grass fire kills a plant it passes over.
   *
   * `GroundFireConfig.damagePerTick` is what this is measured against - a plant tougher than a few ticks of
   * fire would leave a burnt meadow with every herb in it standing, which is the artefact the whole burnable
   * ground work exists to avoid. Deliberately loose: what matters is the order of magnitude, not the number.
   */
  @Test
  fun `a plant dies to a few ticks of fire`() {
    val registry = PropKindRegistry().also { it.load() }

    val perTick = GroundFireConfig().damagePerTick
    val survivable = FIRE_TICKS_TO_KILL_A_PLANT * perTick

    for (kind in PLANTS) {
      val maxHp = registry.of(kind).maxHp
      assertTrue(maxHp <= survivable, "$kind has $maxHp hp against $perTick damage a tick, so it outlasts a fire")
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

  /**
   * Each ground cover kind reaches its own runtime kind, blighted and not.
   *
   * `StaticEntityKind.of` is an exhaustive `when` over `PropKind`, so a *missing* arm is a compile error and
   * needs no test. What a compiler cannot see is an arm pointing at the wrong constant - three near-identical
   * pairs written in a row is exactly where a copy-paste puts a reed's mesh on a herb.
   */
  @Test
  fun `each ground cover kind has its own runtime kind, blighted and not`() {
    val mapped = GROUND_COVER.flatMap { kind ->
      listOf(false, true).map { blighted -> StaticEntityKind.of(kind, blighted, large = false) }
    }

    assertEquals(GROUND_COVER.size * 2, mapped.toSet().size, "two ground cover kinds share a runtime kind: $mapped")
    assertEquals(PLANTS, mapped.toSet())

    for (kind in GROUND_COVER) {
      assertEquals(kind.name, StaticEntityKind.of(kind, blighted = false, large = false).name)
      assertEquals("BLIGHTED_${kind.name}", StaticEntityKind.of(kind, blighted = true, large = false).name)
    }
  }

  private companion object {

    /** worldgen's ground cover kinds. Named here rather than imported so a rename of either side shows up. */
    val GROUND_COVER = listOf(PropKind.HERB, PropKind.SHRUB, PropKind.REED)

    /** Picked once and gone: `shouldEmit` drops a depleted one for good. */
    val MINERALS = setOf(
      StaticEntityKind.MANA_CRYSTAL_SMALL,
      StaticEntityKind.MANA_CRYSTAL_LARGE,
      StaticEntityKind.AETHERITE_SHARD_SMALL,
      StaticEntityKind.AETHERITE_SHARD_LARGE
    )

    /** Picked and grown back. worldgen's ground cover, each kind with its blighted twin. */
    val PLANTS = setOf(
      StaticEntityKind.HERB,
      StaticEntityKind.BLIGHTED_HERB,
      StaticEntityKind.SHRUB,
      StaticEntityKind.BLIGHTED_SHRUB,
      StaticEntityKind.REED,
      StaticEntityKind.BLIGHTED_REED
    )

    /** How many ticks of fire a plant may survive before a burnt meadow looks untouched. */
    const val FIRE_TICKS_TO_KILL_A_PLANT = 3
  }
}
