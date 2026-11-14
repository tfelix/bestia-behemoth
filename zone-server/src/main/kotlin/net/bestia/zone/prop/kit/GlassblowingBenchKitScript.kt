package net.bestia.zone.prop.kit

import net.bestia.zone.prop.PlayerStructureService
import net.bestia.zone.entity.StaticEntityKind
import org.springframework.stereotype.Component

/** The Glassblowing Bench Kit (`items.yml` id 255), made in Handiwork with Carpentry. */
@Component
class GlassblowingBenchKitScript(
  structures: PlayerStructureService
) : StructureKitScript(structures, StaticEntityKind.GLASSBLOWING_BENCH, BUILD_SECONDS) {

  override val itemId = 255L

  private companion object {
    /** Long enough that a build is something you commit to, short enough to stand through. */
    const val BUILD_SECONDS = 15f
  }
}
