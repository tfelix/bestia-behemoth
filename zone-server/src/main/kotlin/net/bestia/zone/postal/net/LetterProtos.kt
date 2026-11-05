package net.bestia.zone.postal.net

import net.bestia.bnet.proto.LetterProto
import net.bestia.zone.postal.LetterView

internal fun LetterView.toProto(): LetterProto.Letter {
  return LetterProto.Letter.newBuilder()
    .setLetterId(letterId)
    .setSenderName(senderName)
    .setSubject(subject)
    .setBody(body)
    .setSentAtEpochSeconds(sentAt.epochSecond)
    .setExpiresAtEpochSeconds(expiresAt.epochSecond)
    .setRead(read)
    .build()
}
