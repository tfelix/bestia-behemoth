package net.bestia.zone.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import net.bestia.zone.cartography.tile.MapTileService
import org.junit.jupiter.api.Test

/**
 * The map renderer runs inside the zone, but needs nothing from it: it draws a generated world. Kept that way,
 * so moving it to a module or a tile service of its own stays a move. `MapTileService` is the zone's adapter.
 */
class CartographyRendererRulesTest {

  private val renderer = arrayOf(
    "net.bestia.zone.cartography.render..",
    "net.bestia.zone.cartography.coverage..",
    "net.bestia.zone.cartography.tile..",
  )

  @Test
  fun `the map renderer depends only on itself and worldgen`() {
    val imported = ClassFileImporter()
      .withImportOption(ImportOption.DoNotIncludeTests())
      .importPackages("net.bestia.zone.cartography")

    classes().that().resideInAnyPackage(*renderer)
      .and().doNotBelongToAnyOf(MapTileService::class.java)
      .should().onlyDependOnClassesThat().resideInAnyPackage(
        *renderer,
        "net.bestia.worldgen..",
        "java..",
        "javax.imageio..",
        "kotlin..",
        "org.jetbrains.annotations..",
        "io.github.oshai.kotlinlogging..",
      )
      .check(imported)
  }
}
