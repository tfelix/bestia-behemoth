package net.bestia.zone.world.stream

import net.bestia.zone.message.SMSG

/**
 * Messages a slice above `world` wants every client to have on connect, sent right after `WorldInfoSMSG`
 * and so before any chunk.
 *
 * A port, because `world` cannot depend on the slices above it: what the ground marks' rules are is the
 * ground's to say.
 */
interface ConnectMessageSource {

  fun connectMessages(): List<SMSG>
}
