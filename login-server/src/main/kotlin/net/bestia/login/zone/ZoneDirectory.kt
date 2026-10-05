package net.bestia.login.zone

/**
 * Which zones an account may be playing on. The login server does not see the game sockets, so it cannot know
 * for sure, and asks every candidate.
 */
fun interface ZoneDirectory {

  fun candidatesFor(accountId: Long): List<ZoneEndpoint>
}
