package net.bestia.worldgen.core

/**
 * A bag of tunables that can fingerprint itself.
 *
 * Implemented by every `*Params` class and by the catalogues that are tunables in all but name. It exists for
 * two reasons beyond documentation: `WorldParams` folds its members through it without naming each one twice,
 * and `ParamsVersionTest` iterates a list of them to assert that every field reaches the digest. Without the
 * interface that test would need two lines per class - the instance and its digest - and the second line is
 * exactly the one somebody forgets to add.
 *
 * The contract, which [ParamsDigest] states at length: **every tunable is a primary-constructor property**, and
 * [digest] folds all of them.
 */
interface Params {

  /** See [ParamsDigest]. The builder rather than its value, so a test can read the field names it folded. */
  fun digest(): ParamsDigest
}

/**
 * A 64-bit fingerprint of a stage's tunables, so that retuning one is not a silent change.
 *
 * ### The hole this fills
 *
 * [WorldGenPipeline] hashes stage ids, the hand-written [Stage.version] ints and the dependency graph. It has
 * never hashed a params object, so changing `oceanicShare` from 0.45 to 0.46 moved terrain and moved **no**
 * version number: not `pipelineVersion`, not the chunk cache key that is derived from it, not the boot gate.
 * The only discipline was remembering to hand-bump the stage's `version` as well, and a discipline that has to
 * be remembered on every value change is one that eventually is not.
 *
 * That mattered little while every tunable was a compile-time default nobody could change without a commit. It
 * matters a great deal once params are loaded from a file, because then two people with different files
 * generate different worlds and nothing in the system can say so - in the one subsystem whose entire debugging
 * method is comparing two runs of it.
 *
 * ### Where the digest goes, and where it must not
 *
 * It reaches [Stage.paramsVersion], and from there the version vector and the chunk cache key. It must
 * **never** reach [GenContext.rng] or any other stream derivation. If it did, changing a value would reseed
 * the world, and "move one number and look at the same world" - the first thing anybody does when tuning -
 * would become impossible. Params decide *what* the arithmetic computes; they are not an input to *which*
 * random numbers it consumes.
 *
 * ### Why the fields are listed by hand
 *
 * Kotlin reflection is not on this module's classpath and will not be, so there is no automatic walk of a
 * `data class`. The two free alternatives are both wrong here:
 *
 * - **`hashCode()`** is 32 bits with a `31 *` fold, which is a poor guard for a number that gates cache
 *   correctness - and fatally, `DoubleArray.hashCode()` is identity-based. One array field anywhere in a
 *   params class would make the digest differ in every JVM process, so `pipelineVersion` would change on every
 *   boot and a server would refuse the world it generated a second earlier.
 * - **`toString()`** renders doubles as decimal text, whose exact form is a property of the runtime. The
 *   version gate exists so a client can generate its own base terrain, and that client is C#, where `1.0`
 *   formats as `1`. Raw IEEE-754 bits are the same 64 bits in every language. [WorldConfig.shapeVersion] made
 *   this call already and this follows it.
 *
 * `toString()` *is* used, in the tests, as the oracle that no field was forgotten: the set of `identifier=`
 * names it renders must equal [names]. That is a comparison of field *names* within one process, where its
 * instability does not apply, and it is what makes the hand-written list safe rather than merely careful.
 *
 * The constraint it imposes, and it is worth knowing before adding a tunable: **a tunable must be a
 * primary-constructor property.** A `val` in the class body appears in neither the digest nor `toString`, so
 * nothing would notice it was missing.
 *
 * ### The rules of the fold
 *
 * - **Names are folded, not only values.** Otherwise swapping the values of `minWarYears` and `maxWarYears`
 *   would produce the same digest as leaving them alone.
 * - **Sorted by name**, so reordering a constructor's parameters is not a change. [WorldGenPipeline] already
 *   folds dependencies in name order for the same reason.
 * - **A duplicate name is refused.** A copy-paste that puts one field's value under another's name would pass
 *   a set comparison against `toString`, which is exactly the failure the oracle is supposed to catch.
 * - **[GenRng.hash] seeds with the argument count**, so adding a field moves the digest even when its value
 *   is zero, and a nullable field's absence is distinguishable from any value it could take.
 *
 * There is deliberately no `put(name, value: Any)`. A type this class cannot fold must be a compile error, not
 * an identity hash that happens to be stable until the day it is not.
 */
class ParamsDigest {

  private val entries = ArrayList<Entry>()

  private class Entry(val name: String, val slots: LongArray)

  /** Every name folded so far, in call order, for the completeness oracle in `ParamsVersionTest`. */
  val names: List<String> get() = entries.map { it.name }

  /**
   * The fingerprint.
   *
   * Computed on demand rather than accumulated, because the fold has to happen in name order and the caller
   * writes its fields in declaration order.
   */
  val value: Long
    get() {
      val sorted = entries.sortedBy { it.name }
      val folded = LongArray(sorted.sumOf { 1 + it.slots.size })

      var i = 0
      for (entry in sorted) {
        folded[i++] = GenRng.hashString(entry.name)
        for (slot in entry.slots) folded[i++] = slot
      }

      return GenRng.hash(*folded)
    }

  /**
   * A `Double`, by its raw bits.
   *
   * Two slots rather than one: a presence flag, then the bits. The flag is constant for a non-nullable field
   * and is there so this and [putOrDerived] fold to the same shape, which means making a tunable nullable
   * later is a change to what the digest *contains* rather than to how it is laid out.
   */
  fun put(name: String, value: Double): ParamsDigest = add(name, PRESENT, value.toRawBits())

  /**
   * A `Double?`, where null means "derive it from the world" - `TectonicsParams.plateSpacing` and friends.
   *
   * The absence is a flag rather than a sentinel value, because NaN payloads make every 64-bit pattern
   * reachable from some `toRawBits()`, so there is no Long that cannot also be a real double.
   *
   * Note this folds the *declared* value, not the derived one: a generator that says "derive the plate spacing
   * from the world size" is a different generator from one that says "700 km", even on the world where the two
   * coincide. The world-size half of that is already in [WorldConfig.shapeVersion].
   */
  fun putOrDerived(name: String, value: Double?): ParamsDigest =
    if (value == null) add(name, ABSENT, 0L) else add(name, PRESENT, value.toRawBits())

  fun put(name: String, value: Int): ParamsDigest = add(name, PRESENT, value.toLong())

  fun put(name: String, value: Boolean): ParamsDigest = add(name, PRESENT, if (value) 1L else 0L)

  fun put(name: String, value: String): ParamsDigest = add(name, PRESENT, GenRng.hashString(value))

  /**
   * An enum, by its **name**.
   *
   * Not its ordinal: a reorder of an enum whose ordinal is not stored anywhere is not a change to the world
   * and must not read as one, while a rename is a change to the meaning of the field and must. Where an
   * ordinal *is* load bearing - `Biome` in the BIOME raster, the index into `Culture.ALL` in a station channel
   * - the ordering is folded separately by that catalogue's own digest, in list order.
   */
  fun put(name: String, value: Enum<*>): ParamsDigest = add(name, PRESENT, GenRng.hashString(value.name))

  /** A list of doubles, length included, so a shorter list is never a prefix collision of a longer one. */
  fun put(name: String, values: List<Double>): ParamsDigest {
    val slots = LongArray(values.size + 1)
    slots[0] = values.size.toLong()
    for (i in values.indices) slots[i + 1] = values[i].toRawBits()
    return add(name, slots)
  }

  /**
   * Another digest, for a nested params object or a catalogue.
   *
   * A separate name from [put] so a call site cannot silently fold a nested digest as though it were an `Int`
   * tunable, and so that `grep nested` finds every composition point.
   */
  fun nested(name: String, digest: Long): ParamsDigest = add(name, PRESENT, digest)

  private fun add(name: String, presence: Long, bits: Long) = add(name, longArrayOf(presence, bits))

  private fun add(name: String, slots: LongArray): ParamsDigest {
    require(name.isNotBlank()) { "a digested field needs a name" }
    require(entries.none { it.name == name }) {
      "$name is digested twice - one field's value is almost certainly under another field's name, " +
          "which a set comparison against toString() cannot see"
    }

    entries.add(Entry(name, slots))
    return this
  }

  private companion object {
    const val PRESENT = 1L
    const val ABSENT = 0L
  }
}
