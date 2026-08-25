package net.bestia.zone.world.prop

import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.poi.PoiKind
import net.bestia.worldgen.voxel.PropKind

/**
 * A kind of entity that is placed once and then stands still.
 *
 * Deliberately wider than worldgen's [PropKind]: that enum names what the *generator* emits, and this one
 * names everything that reaches a client through the static entity channel - which will also carry the walls
 * and structures players build. Those are ordinary persisted entities with nothing generated about them, and
 * they want the same cheap per-chunk delivery, so the delivery mechanism has to be keyed on something broader
 * than "what the generator produced".
 *
 * The buildings a town is made of were the other half of that sentence, and they have now arrived - see
 * [BUILDING_KINDS].
 *
 * The ordinal reaches the wire, so **append only**.
 */
enum class StaticEntityKind {

  TREE,
  BLIGHTED_TREE,
  MANA_CRYSTAL_SMALL,
  MANA_CRYSTAL_LARGE,
  WOUND_SPIRE,
  AETHERITE_SHARD_SMALL,
  AETHERITE_SHARD_LARGE,

  // The points of interest, one per entry in worldgen's `PoiKind` and joined to it by name - see [POI_KINDS].
  // A constant each rather than one `POI` kind plus a discriminator, because a client dispatches a *mesh* on
  // this value and a broken obelisk has nothing in common with a sunken idol. It is also what gives each of them
  // its own `prop-kinds.yml` row, so the collider a waystone eventually needs is not shared with a grave's.
  POI_LOST_GRAVE,
  POI_STANDING_STONES,
  POI_BROKEN_OBELISK,
  POI_WAYSTONE,
  POI_PETRIFIED_TREE,
  POI_SUNKEN_IDOL,

  // The buildings a town lays out, one per entry in worldgen's `BuildingFunction` and joined to it by name -
  // see [BUILDING_KINDS]. `FORTIFICATION` is absent on purpose and is the one function with no constant here:
  // a keep and a wall tower are still voxels, because a wall circuit an agent can walk through is not a wall.
  //
  // A constant each rather than one `BUILDING` kind plus a discriminator, for the reason the POI block above
  // gives: a client dispatches a *mesh* on this value, and a barn has nothing in common with a temple. It is
  // also what gives each of them its own `prop-kinds.yml` row, so the interaction a shop eventually needs -
  // a door, an owner, a stock of goods - is not shared with a warehouse's.
  BUILDING_MARKET,
  BUILDING_TEMPLE,
  BUILDING_CIVIC,
  BUILDING_SHOP,
  BUILDING_CRAFT,
  BUILDING_WAREHOUSE,
  BUILDING_INN,
  BUILDING_RESIDENCE,
  BUILDING_FARM,

  // The crafting stations a player puts up, and the first entries here that no generator produces - which is
  // what the class note above has been describing since it was written. They reach a client through the same
  // per-chunk static entity channel as a tree, and they come from `PlayerStructureSource` rather than from
  // `GeneratedPropSource`, so their `WorldObjectSite.propId` is 0.
  WORKBENCH,
  FURNACE,
  FORGE,

  // The ground cover: worldgen's HERB, SHRUB and REED, each with its blighted twin. Split on `blighted` like
  // the trees and for the trees' reason - a corrupted herb is a different model, not a tint.
  //
  // A blighted *reed* looks like a kind nothing can ask for, since corruption is zero over water. It is not:
  // a reed stands on the shore rather than in the lake, corruption is sampled bilinearly, and a shore in a
  // corrupted province reads a non-zero value. Uniform with the other two is cheaper than an exception whose
  // premise does not hold.
  HERB,
  BLIGHTED_HERB,
  SHRUB,
  BLIGHTED_SHRUB,
  REED,
  BLIGHTED_REED;

  companion object {

    /**
     * The runtime kind for a generated prop.
     *
     * Splits a generator kind into several constants wherever its *flags* are a visual: a blighted tree and a
     * green one are different models, and the two crystal sizes are different meshes. Keeping that split here
     * rather than sending the flags means a client dispatches on one value instead of decoding a bitfield, and
     * one more variant costs one enum constant.
     *
     * The counts deliberately go unstated. This said "three kinds into five" while the generator had four and
     * this had seven, because a number in a comment is a number nobody updates.
     */
    fun of(kind: PropKind, blighted: Boolean, large: Boolean, subKind: Int = 0): StaticEntityKind = when (kind) {
      PropKind.TREE -> if (blighted) BLIGHTED_TREE else TREE
      PropKind.MANA_CRYSTAL -> if (large) MANA_CRYSTAL_LARGE else MANA_CRYSTAL_SMALL
      PropKind.WOUND_SPIRE -> WOUND_SPIRE
      // Split on size like the crystals and **not** on `blighted`, which is always true for a shard: the
      // generator sets that flag by construction because corrupted rock is what makes a body yield aetherite
      // at all, so a clean variant would be a mesh nothing can ever ask for.
      PropKind.AETHERITE_SHARD -> if (large) AETHERITE_SHARD_LARGE else AETHERITE_SHARD_SMALL
      // The only kind that splits on `subKind` rather than on the flags, and the only one whose flags carry
      // nothing: a POI is a specific object rather than a sample of a field. `PoiKind`'s four bits of kind would
      // not have held a dozen landmarks, so the generator gives them one `PropKind` and a byte - see
      // `PropInstances.subKindAt`.
      PropKind.POI -> POI_KINDS[subKind]
      // `BuildingFunction`'s ordinal, on `subKind` for the same reason a POI's kind is: sixteen `PropKind`s do
      // not stretch to a landmark catalogue *and* ten building functions.
      //
      // The null is `FORTIFICATION`, and it is a hard failure rather than a fallback. A fortification is
      // voxels, so `BuildingProps` filters it out before it can ever become a prop; one arriving here means
      // that filter and this table have drifted apart, and quietly drawing it as a residence would put a
      // farmhouse where a gate tower should be and tell nobody.
      PropKind.BUILDING -> requireNotNull(BUILDING_KINDS[subKind]) {
        "BuildingFunction.${BuildingFunction.entries[subKind]} has no StaticEntityKind; it should never " +
            "have been emitted as a prop - see BuildingProps"
      }
      // No table for these three, unlike the POI and building blocks above, and the difference is that those
      // dispatch on a *different* enum's ordinal - which a `when` cannot see, so they need a name join checked
      // at class load. `PropKind` is this `when`'s own subject, so a kind appended in worldgen without an arm
      // here is a compile error, which is the stronger guard.
      PropKind.HERB -> if (blighted) BLIGHTED_HERB else HERB
      PropKind.SHRUB -> if (blighted) BLIGHTED_SHRUB else SHRUB
      PropKind.REED -> if (blighted) BLIGHTED_REED else REED
    }

    /**
     * The runtime kind for each entry in worldgen's `PoiKind`, indexed by its ordinal.
     *
     * **Joined by name, checked at class load.** A landmark added to the catalogue with no constant here would
     * otherwise be an `IndexOutOfBounds` on whichever world first rolled it - one seed in three, discovered by a
     * player rather than by a build - and the name join means the constant cannot be silently mismatched either.
     * `PropKindRegistry.load` fails the boot on a missing `prop-kinds.yml` row for the same reason.
     */
    private val POI_KINDS: List<StaticEntityKind> = PoiKind.entries.map { poi ->
      val name = "POI_${poi.name}"
      requireNotNull(entries.firstOrNull { it.name == name }) {
        "worldgen's PoiKind.${poi.name} has no StaticEntityKind.$name; append one and give it a prop-kinds.yml row"
      }
    }

    /**
     * The runtime kind for each entry in worldgen's `BuildingFunction`, indexed by its ordinal.
     *
     * Joined by name and checked at class load, exactly as [POI_KINDS] is, and **nullable where that one is
     * not**: `FORTIFICATION` is a building the generator never turns into a prop, so a missing constant is the
     * correct answer for it rather than a gap to fail on. Every *other* function must have one, and a new
     * function added to worldgen with no constant here fails the boot rather than a player's chunk.
     */
    private val BUILDING_KINDS: List<StaticEntityKind?> = BuildingFunction.entries.map { function ->
      val name = "BUILDING_${function.name}"
      val kind = entries.firstOrNull { it.name == name }
      require(kind != null || function == BuildingFunction.FORTIFICATION) {
        "worldgen's BuildingFunction.${function.name} has no StaticEntityKind.$name; append one and give it " +
            "a prop-kinds.yml row, or exclude it in BuildingProps the way FORTIFICATION is"
      }
      kind
    }
  }
}
