package net.bestia.zone.item.script

import net.bestia.zone.world.prop.PlayerStructureService
import net.bestia.zone.world.prop.StaticEntityKind
import org.springframework.stereotype.Component

/**
 * The Workbench Kit (`items.yml` id 26): the first way to get a workbench that is not the Carpentry skill.
 *
 * Carpentry raises one outright and needs the Craftsman tree; this is the route for a player who has not
 * taken it, and it costs time on site instead.
 */
@Component
class WorkbenchKitScript(
  structures: PlayerStructureService
) : StructureKitScript(structures, StaticEntityKind.WORKBENCH, BUILD_SECONDS) {

  override val itemId = 26L

  private companion object {
    /** Long enough that a build is something you commit to, short enough to stand through. */
    const val BUILD_SECONDS = 20f
  }
}
