package net.bestia.zone.architecture

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import net.bestia.zone.BestiaException
import net.bestia.zone.architecture.ZoneClasses.ROOT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** What each part of the zone may depend on. The packages at the bottom depend on nothing above them. */
class FeatureSliceRulesTest {

  @Test
  fun `util and geometry depend on nothing else in the zone`() {
    val leaves = arrayOf("$ROOT.util..", "$ROOT.geometry..")

    classes().that().resideInAnyPackage(*leaves)
      .should().onlyDependOnClassesThat(resideOutsideOfPackage("$ROOT..").or(resideInAnyPackage(*leaves)))
      .check(ZoneClasses.main)
  }

  /** The kernel the whole zone runs on: no Spring, no messages, no feature. */
  @Test
  fun `the ecs kernel depends only on itself, util and the JDK`() {
    classes().that().resideInAPackage("$ROOT.ecs.core..")
      .should().onlyDependOnClassesThat(
        resideInAnyPackage("$ROOT.ecs.core..", "$ROOT.util..", "java..", "kotlin..", "org.jetbrains.annotations..",
          "io.github.oshai.kotlinlogging..")
          .or(equivalentTo(BestiaException::class.java))
      )
      .check(ZoneClasses.main)
  }

  @Test
  fun `the configs depend only on the ecs kernel and Spring`() {
    classes().that().resideInAPackage("$ROOT.config..")
      .should().onlyDependOnClassesThat(
        resideInAnyPackage("$ROOT.config..", "$ROOT.ecs.core..", "java..", "kotlin..", "org.jetbrains.annotations..",
          "org.springframework..")
      )
      .check(ZoneClasses.main)
  }

  @Test
  fun `every top-level package has a tier`() {
    val untiered = ZoneClasses.main.map { sliceOf(it) }.filter { it !in TIERS }.toSortedSet()

    assertEquals(sortedSetOf<String>(), untiered, "Add these packages to TIERS")
  }

  /** A slice that needs something from a higher slice declares a port and lets the higher slice implement it. */
  @Test
  fun `slices only depend on lower tiers`() {
    val found = backEdges()

    val added = found.keys - KNOWN_BACK_EDGES
    val fixed = KNOWN_BACK_EDGES - found.keys
    assertTrue(added.isEmpty() && fixed.isEmpty()) {
      "New back edges:\n" + added.joinToString("\n") { "  $it (${found[it]})" } +
        "\nBack edges that are gone, remove them from KNOWN_BACK_EDGES:\n" + fixed.joinToString("\n") { "  $it" }
    }
  }

  /** Each back edge with one dependency that causes it, so a failure says where to look. */
  private fun backEdges(): Map<String, String> {
    val found = sortedMapOf<String, String>()
    for (origin in ZoneClasses.main) {
      val from = sliceOf(origin)
      for (dependency in origin.directDependenciesFromSelf) {
        val target = dependency.targetClass
        if (!isZone(target)) {
          continue
        }
        val to = sliceOf(target)
        if (TIERS.indexOf(to) > TIERS.indexOf(from)) {
          found.putIfAbsent("$from -> $to", dependency.description)
        }
      }
    }
    return found
  }

  private fun isZone(javaClass: JavaClass): Boolean {
    return javaClass.packageName == ROOT || javaClass.packageName.startsWith("$ROOT.")
  }

  private fun sliceOf(zoneClass: JavaClass): String {
    return zoneClass.packageName.removePrefix(ROOT).removePrefix(".").substringBefore('.').ifEmpty { "root" }
  }

  private companion object {
    /** The slices from the bottom up. `ecs` is the kernel `ecs.core`. */
    val TIERS = listOf(
      "root", "util", "geometry", "ecs", "config", "message", "session", "sync", "persistence",
      "identity", "aoi", "entity", "logout", "navigation", "movement", "world", "script", "place", "skill", "dialog",
      "battle", "weather", "ground", "item", "bestia", "spoor", "prop", "casting", "ai", "spawn",
      "account", "party", "economy", "crafting", "cartography", "townsfolk", "master", "respawn", "trade", "capture",
      "chat", "control", "internal", "socket", "metrics", "engine", "boot",
    )

    /** Back edges not broken yet. The list must only shrink. */
    val KNOWN_BACK_EDGES = setOf(
      "ai -> spawn", "ai -> townsfolk",
      "economy -> townsfolk",
      "identity -> account",
      "spawn -> townsfolk",
    )
  }
}
