package net.bestia.zone.prop.omen

import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.prop.PropKindRegistry
import net.bestia.zone.prop.WorldObjectSite
import net.bestia.zone.prop.WorldObjectSource
import org.springframework.stereotype.Component

/** The scars the mana left, served to whoever looks at their chunk, like the trees. */
@Component
class OmenScarSource(
  private val scars: OmenScars,
  private val propKinds: PropKindRegistry,
) : WorldObjectSource {

  override val kinds: Set<StaticEntityKind> = StaticEntityKind.entries.filter { it.isOmen }.toSet()

  override fun sitesIn(chunk: ChunkPos): List<WorldObjectSite> = scars.inColumn(chunk.x, chunk.y).map(::siteOf)

  /**
   * Neither a generated prop nor a player's structure, so both ids are 0. Nothing keys on them: a scar cannot be
   * broken, see `PropPromotionService`.
   */
  fun siteOf(scar: OmenScars.Scar): WorldObjectSite = WorldObjectSite(
    kind = scar.kind,
    propId = 0,
    position = scar.position,
    variant = scar.variant,
    heightDm = (propKinds.of(scar.kind).collider.height * 10).toInt(),
    yaw = scar.yaw,
  )
}
