package net.bestia.zone.poi

import io.mockk.mockk
import net.bestia.worldgen.poi.PoiKind
import net.bestia.zone.poi.script.ExaminePoiScript
import net.bestia.zone.prop.PropKindRegistry
import net.bestia.zone.script.EntityScriptRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PoiCatalogTest {

  private val kinds = PropKindRegistry().also { it.load() }

  @Test
  fun `every POI kind names a script that exists`() {
    val catalog = PoiCatalog(kinds, EntityScriptRegistry(listOf(ExaminePoiScript(mockk()))))

    catalog.validate()

    assertEquals("ExaminePoiScript", catalog.configOf(PoiKind.BROKEN_OBELISK).script)
  }

  @Test
  fun `a script name without a bean fails validation and names the row`() {
    val catalog = PoiCatalog(kinds, EntityScriptRegistry(emptyList()))

    val error = assertThrows<IllegalStateException> { catalog.validate() }

    assertTrue("ExaminePoiScript" in error.message.orEmpty(), error.message)
  }
}
