package net.bestia.api.rest

import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.ZoneAnswer
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/** A zone reply's answer as [T], or the HTTP error its status stands for. */
inline fun <reified T : ZoneAnswer> BusReply.answerOrThrow(): T {
  when (status) {
    BusReply.Status.OK, BusReply.Status.NOT_MODIFIED -> {}
    BusReply.Status.NOT_FOUND -> throw ResponseStatusException(HttpStatus.NOT_FOUND)
    BusReply.Status.INVALID -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, detail)
    BusReply.Status.BUSY -> throw BusyZoneException()
  }

  return answer as? T ?: throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "The zone answered ${answer?.let { it::class.simpleName }}")
}
