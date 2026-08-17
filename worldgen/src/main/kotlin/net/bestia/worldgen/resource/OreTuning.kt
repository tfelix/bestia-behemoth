package net.bestia.worldgen.resource

import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.ParamsText

/**
 * Per-ore overrides for how much of a resource a world holds, how widely it is looked for, and how many of it
 * every world is promised whatever its geology.
 *
 * ### Why the enum was not enough
 *
 * [MinableOre] carries all three numbers already, and they are the right place for a *default*: they are facts
 * the generator states about a mineral, sit beside the geology that explains them, and are folded into
 * `MinableOre.catalogueDigest` so a change to one is a change to the world. What they are not is *tunable*.
 * `ResourceParams` has had `tonnageScale` since the ore band landed - one multiplier over every deposit in the
 * world - and `candidateSpacing`, which scales every ore's sampling together. Neither can say "there should be
 * more diamond", which is the question anyone tuning a world actually asks.
 *
 * So this is the missing half: the same numbers, per ore, in the params file.
 *
 * ```
 * resource.ore.diamond.abundance = 0.06    # tonnes per thousand km2, the world's total for it
 * resource.ore.diamond.spacing   = 1.3     # candidate spacing multiplier - smaller looks in more places
 * resource.ore.diamond.floor     = 2       # deposits every world gets, however badly it suits diamond
 * resource.ore.ruby.abundance    = 0.14
 * ```
 *
 * ### The three knobs answer different questions
 *
 * [abundanceOf] is the world's **total tonnage** of an ore, split between however many deposits it got - so it
 * decides how rich each find is, and not at all how many finds there are. [spacingOf] is the candidate lattice
 * the suitability field thins, so it decides how many *places* are considered and therefore how many deposits a
 * typical world ends up with. Doubling the first gives the same number of twice-as-good mines; halving the
 * second gives about four times as many mines of the same richness. Somebody who wants "more diamond" almost
 * always means the second.
 *
 * [floorOf] is not a density at all: it is the promise that the world has any of this ore whatsoever, and it
 * only ever comes into play on a world whose sampler came up short. Raising it does nothing to a world that was
 * already comfortable. It is also the one of the three that competes - see [MinableOre.guaranteedDeposits] for
 * what a floor costs the ores next to it.
 *
 * ### Absence is the enum's value, not a zero
 *
 * A key nobody sets reads back as [MinableOre]'s own number, which is [ParamsText]'s idiom throughout: the
 * reader passes its current value as the fallback, so the defaults stay in exactly one place. That also makes
 * this safe to add to - a new ore needs no row here.
 */
data class OreTuning(
  private val abundance: Map<MinableOre, Double> = emptyMap(),
  private val spacing: Map<MinableOre, Double> = emptyMap(),
  private val floor: Map<MinableOre, Int> = emptyMap()
) : Params {

  init {
    for ((ore, tons) in abundance) {
      require(tons > 0.0) { "${key(ore)}.abundance must be positive, was $tons" }
    }
    for ((ore, factor) in spacing) {
      require(factor > 0.0) { "${key(ore)}.spacing must be positive, was $factor" }
    }
    for ((ore, count) in floor) {
      require(count >= 0) { "${key(ore)}.floor must not be negative, was $count" }
    }
  }

  /** Tonnes per thousand square kilometres of world, for [ore]. */
  fun abundanceOf(ore: MinableOre): Double = abundance[ore] ?: ore.tonsPerThousandSqKm

  /** Candidate spacing multiplier for [ore]. Smaller looks in more places. */
  fun spacingOf(ore: MinableOre): Double = spacing[ore] ?: ore.spacingFactor

  /** Deposits of [ore] every world is promised, however badly its geology suits it. Zero disables the floor. */
  fun floorOf(ore: MinableOre): Int = floor[ore] ?: ore.guaranteedDeposits

  /** The widest candidate spacing any ore asks for, which is what the small-world floor is measured against. */
  fun coarsestSpacing(): Double = MinableOre.entries.maxOf { spacingOf(it) }

  fun overriddenBy(source: ParamsText.ParamsSource): OreTuning {
    val tons = LinkedHashMap<MinableOre, Double>()
    val factors = LinkedHashMap<MinableOre, Double>()
    val floors = LinkedHashMap<MinableOre, Int>()

    for (ore in MinableOre.entries) {
      val scope = source.scope(key(ore))

      // Asked for whether or not the file sets it, which is what registers the key as part of the schema -
      // see ParamsText's note on the schema being derived from what readers ask for. A misspelled ore name is
      // then an unknown key with a line number and a suggestion, rather than a value that silently does nothing.
      val theirTons = scope.double("abundance", ore.tonsPerThousandSqKm)
      val theirSpacing = scope.double("spacing", ore.spacingFactor)
      val theirFloor = scope.int("floor", ore.guaranteedDeposits)

      if (theirTons != ore.tonsPerThousandSqKm) tons[ore] = theirTons
      if (theirSpacing != ore.spacingFactor) factors[ore] = theirSpacing
      if (theirFloor != ore.guaranteedDeposits) floors[ore] = theirFloor
    }

    return OreTuning(tons, factors, floors)
  }

  /**
   * Folds the **effective** numbers, in enum order, rather than only the overrides.
   *
   * So the digest moves when a file changes a value and also when the enum's own default moves, and two worlds
   * built from different files but the same effective numbers agree - which is the property a digest is for.
   * `MinableOre.catalogueDigest` folds the enum's side of it too and the overlap is deliberate: that one has to
   * keep working for the depths and ranks this class does not touch.
   */
  override fun digest(): ParamsDigest {
    val digest = ParamsDigest()

    for (ore in MinableOre.entries) {
      digest.put("${key(ore)}.abundance", abundanceOf(ore))
      digest.put("${key(ore)}.spacing", spacingOf(ore))
      digest.put("${key(ore)}.floor", floorOf(ore).toDouble())
    }

    return digest
  }

  private companion object {

    /** The ore's key in a params file. Lower case, because a file is read by people and `DIAMOND` shouts. */
    fun key(ore: MinableOre): String = ore.name.lowercase()
  }
}
