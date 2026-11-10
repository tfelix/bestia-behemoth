package net.bestia.zone.breeding

import net.bestia.zone.ecs.core.World
import net.bestia.zone.master.skill.BloodlineStudy
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.skill.ecs.KnownSkills
import org.springframework.stereotype.Component
import java.time.Clock

/** Tells the owner of a Breeding Farm or Incubator what it is doing, if they are online as the master who owns it. */
@Component
class BreedingStateSender(
  private val structures: PlayerStructureRegistry,
  private val farms: BreedingFarms,
  private val incubators: Incubators,
  private val connections: ConnectionInfoService,
  private val skills: BreederSkills,
  private val out: OutMessageProcessor,
  private val clock: Clock = Clock.systemUTC(),
) {

  fun sendFarm(world: World, structureId: Long) {
    val accountId = onlineOwnerOf(structureId) ?: return
    out.sendToPlayer(accountId, BreedingFarmSMSG.of(farms.of(structureId), clock.instant(), readsExactIvs(world, accountId)))
  }

  fun sendIncubator(world: World, structureId: Long) {
    val accountId = onlineOwnerOf(structureId) ?: return
    out.sendToPlayer(accountId, IncubatorSMSG.of(incubators.of(structureId), clock.instant(), readsExactIvs(world, accountId)))
  }

  /** A notice for the owner, such as a failed clutch. */
  fun notify(structureId: Long, msg: SMSG) {
    onlineOwnerOf(structureId)?.let { out.sendToPlayer(it, msg) }
  }

  private fun readsExactIvs(world: World, accountId: Long): Boolean {
    val masterEntityId = connections.getSelectedMasterEntityId(accountId)
    return BloodlineStudy.readsExactIvs(skills.bloodlineStudyLevel(world.get(masterEntityId, KnownSkills::class)))
  }

  private fun onlineOwnerOf(structureId: Long): Long? {
    val structure = structures.of(structureId) ?: return null
    val accountId = structure.ownerAccountId ?: return null

    return accountId.takeIf { connections.activeMasterIdOf(it) == structure.ownerMasterId }
  }
}
