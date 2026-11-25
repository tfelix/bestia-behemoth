package net.bestia.zone.worldevent.gm

import net.bestia.account.Authority
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.place.ecs.ProvinceService
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.AccountId
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaConcentrationService
import net.bestia.zone.world.mana.ManaField
import net.bestia.zone.world.mana.ManaField.Cell
import net.bestia.zone.world.mana.ManaTiers
import net.bestia.zone.worldevent.WorldEventDirector
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Sends the GMs who asked for it the mana field and the director's state around them, for their map. The authority
 * is checked again before every send, so a GM who lost it, or logged out, stops receiving the layer.
 *
 * Off the tick: the field has its own lock and the director's rows are read like the director itself reads them.
 */
@Component
class GmManaLayer(
  private val connections: ConnectionInfoService,
  private val world: WorldView,
  private val worldService: WorldService,
  private val mana: ManaConcentrationService,
  private val tiers: ManaTiers,
  private val provinces: ProvinceService,
  private val director: WorldEventDirector,
  private val out: OutMessageProcessor,
) {

  private val watching = ConcurrentHashMap.newKeySet<AccountId>()

  fun turnOn(accountId: AccountId) {
    watching += accountId
    send(accountId)
  }

  fun turnOff(accountId: AccountId) {
    watching -= accountId
    out.sendToPlayer(accountId, GmManaLayerSMSG.CLEARED)
  }

  @Scheduled(fixedDelay = SEND_EVERY_MS, initialDelay = SEND_EVERY_MS)
  fun sendAll() {
    if (watching.isEmpty() || !worldService.isLoaded) return
    watching.forEach(::send)
  }

  private fun send(accountId: AccountId) {
    if (Authority.WORLD_EVENTS !in connections.getAuthorities(accountId)) {
      watching -= accountId
      return
    }
    val at = world.read {
      get(connections.getSelectedMasterEntityId(accountId), Position::class)?.toVec3L()
    } ?: return

    out.sendToPlayer(accountId, layerAround(mana.cellAt(at)))
  }

  private fun layerAround(centre: Cell): GmManaLayerSMSG {
    val west = centre.x - REACH_CELLS
    val south = centre.y - REACH_CELLS
    val width = 2 * REACH_CELLS + 1

    val cells = (0 until width * width).map { i ->
      val cell = Cell(west + i % width, south + i / width)
      val province = provinces.provinceAt(cell.centreX, cell.centreY)?.index ?: NO_PROVINCE
      GmManaLayerSMSG.Cell(mana.densityOf(cell), tiers.tierOf(cell), province)
    }

    return GmManaLayerSMSG(
      westMetres = west * ManaField.CELL_METRES,
      southMetres = south * ManaField.CELL_METRES,
      cellMetres = ManaField.CELL_METRES,
      width = width,
      cells = cells,
      provinces = provincesOf(cells.map { it.province }.toSet() - NO_PROVINCE),
      events = director.running().map { GmManaLayerSMSG.Event(it.id, it.eventId, it.x.toDouble(), it.y.toDouble(), it.provinceIndex) },
    )
  }

  private fun provincesOf(indices: Set<Int>): List<GmManaLayerSMSG.Province> {
    val states = director.provinceStates()

    return provinces.provinces.provinces.filter { it.index in indices }.map { province ->
      val state = states[province.index]
      GmManaLayerSMSG.Province(
        province.index, province.name, state?.budget ?: 0.0, state?.quietSeconds ?: 0L, provinces.isSanctuary(province),
      )
    }
  }

  private companion object {
    const val SEND_EVERY_MS = 5_000L

    /** Cells on each side of the GM's: 20 cells of 128 m show about 2.5 km each way. */
    const val REACH_CELLS = 20

    const val NO_PROVINCE = -1
  }
}
