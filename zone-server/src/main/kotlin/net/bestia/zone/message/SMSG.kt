package net.bestia.zone.message

import net.bestia.bnet.proto.EnvelopeProto

/**
 * Marker interface to make clear this message is an outgoing message that is directed towards connected player
 * clients.
 * SMSG stands  for server message and is a message from the server to the clients.
 */
interface SMSG {
  fun toBnetEnvelope(): EnvelopeProto.Envelope
}