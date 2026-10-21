package net.bestia.api.rest

/** The zone is at its limit. Answered 503 with a Retry-After, by [ApiExceptionHandler]. */
class BusyZoneException : RuntimeException("The zone is busy")
