package net.bestia.zone.ecs.spawn.townsfolk

import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.history.Names
import net.bestia.zone.ai.knowledge.ChronicleNames
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service

/**
 * What a townsperson is called, and the seed everything else individual about them hangs off.
 *
 * One place rather than two, because the name is derived rather than stored: a hover label computed
 * separately from the one a conversation greets you with would drift the moment either salt moved, and
 * nothing would catch it - both would look like names.
 */
@Service
class TownsfolkNaming(private val worldService: WorldService) {

  /** Their name, their voice, which greeting they use: all of it is drawn off this. */
  fun seedOf(identity: Long): Long {
    return GenRng.hash(worldService.record.seed, identity, PERSON_SALT)
  }

  fun nameOf(identity: Long): String {
    val settlement = TownsfolkIdentity.settlementOf(identity)
    val culture = ChronicleNames.cultureOfSettlement(worldService.generated.world.chronicle, settlement)

    return Names.townsperson(seedOf(identity), culture)
  }

  private companion object {
    /** Was `SpeakerResolver.DIALOG_SALT`. Keep the value: a new one renames every townsperson alive. */
    const val PERSON_SALT = 0xD1A706L
  }
}
