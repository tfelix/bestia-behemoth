package net.bestia.zone.landmark

import io.mockk.mockk
import net.bestia.worldgen.poi.PoiKind
import net.bestia.zone.landmark.script.ExamineLandmarkScript
import net.bestia.zone.prop.PropKindRegistry
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LandmarkScriptRegistryTest {

  private val kinds = PropKindRegistry().also { it.load() }

  @Test
  fun `every landmark kind names a script that exists`() {
    val examine = ExamineLandmarkScript(mockk())
    val registry = LandmarkScriptRegistry(listOf(examine), kinds)

    registry.validate()

    assertSame(examine, registry.scriptOf(PoiKind.BROKEN_OBELISK))
  }

  @Test
  fun `a script name without a bean fails validation and names the row`() {
    val registry = LandmarkScriptRegistry(emptyList(), kinds)

    val error = assertThrows<IllegalStateException> { registry.validate() }

    assertTrue("ExamineLandmarkScript" in error.message.orEmpty(), error.message)
  }
}
