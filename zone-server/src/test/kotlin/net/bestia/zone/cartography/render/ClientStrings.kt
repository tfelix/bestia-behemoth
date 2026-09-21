package net.bestia.zone.cartography.render

import java.io.File
import kotlin.test.assertTrue

/**
 * The client's own string table, read off disk.
 *
 * `bestia-client` compiles these into a separately released artefact, so a server-side test is the only place
 * the two can be compared at all - the same position `ClientWorldContractTest` is in about chunk geometry.
 */
object ClientStrings {

  const val GENERAL_CSV = "bestia-client/src/Localization/general.csv"

  /** The key column, which is what Godot's `tr()` looks up. */
  fun keys(): Set<String> {
    val csv = repoRoot().resolve(GENERAL_CSV)
    assertTrue(csv.isFile, "No client string table at ${csv.absolutePath}")

    return csv.readLines().drop(1)
      .mapNotNull { line -> line.substringBefore(',').trim().takeIf { it.isNotEmpty() } }
      .toSet()
  }

  /** Walks up from the working directory, which Gradle sets to the module rather than to the checkout. */
  private fun repoRoot(): File {
    var dir: File? = File("").absoluteFile
    while (dir != null && !File(dir, "bestia-client").isDirectory) dir = dir.parentFile
    return dir ?: error("No checkout root above ${File("").absolutePath}")
  }
}
