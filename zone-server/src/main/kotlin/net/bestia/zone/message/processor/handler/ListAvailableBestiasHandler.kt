package net.bestia.zone.message.processor.handler

/*
@Component
class ListAvailableBestiasHandler(
  private val outMessageProcessor: OutMessageProcessor,
  private val connectionInfoService: ConnectionInfoService,
  private val slotService: AvailableBestiaSlotService,
  private val playerBestiaRepository: PlayerBestiaRepository,
) : InMessageProcessor.IncomingMessageHandler<GetBestias> {
  override val handles = GetBestias::class

  @Transactional
  override fun handle(msg: GetBestias): Boolean {
    LOG.trace { "RX: $msg" }
    val selectedMasterEntityId = try {
      connectionInfoService.getMasterEntityId(msg.playerId)
    } catch (e: IllegalArgumentException) {
      LOG.warn { "No selected master found for ${msg.playerId}" }
      return false
    }

    val ownedEntities = connectionInfoService.getOwnedEntities(msg.playerId)
    val entityIdByPlayerBestiaId = ownedEntities.associate { it.playerBestiaId to it.entityId }
    val playerBestias = playerBestiaRepository.findAllById(ownedEntities.map { it.playerBestiaId })

    val availableBestias = AvailableBestias(
      masterEntityId = selectedMasterEntityId,
      maxAvailableSlots = slotService.getTotalSlotCount(msg.playerId),
      bestias = playerBestias.map { pb ->
        val entityId = entityIdByPlayerBestiaId[pb.id]
          ?: throw IllegalStateException()

        toBestiaInfo(entityId, pb)
      }
    )

    LOG.debug { "Listing bestia for account ${msg.playerId}: $availableBestias" }

    outMessageProcessor.sendToPlayer(msg.playerId, availableBestias)

    return true
  }

  private fun toBestiaInfo(
    playerBestiaEntityId: EntityId,
    playerBestia: PlayerBestia
  ): AvailableBestias.BestiaInfo {
    return AvailableBestias.BestiaInfo(
      name = playerBestia.name,
      playerBestiaId = playerBestia.id,
      modelIdentifier = playerBestia.bestia.identifier,
      playerBestiaEntityId = playerBestiaEntityId,
      level = playerBestia.level,
      position = playerBestia.position // Maybe get this info from the world system to have real time info?
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
*/