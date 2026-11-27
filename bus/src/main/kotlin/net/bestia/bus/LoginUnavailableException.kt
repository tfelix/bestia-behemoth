package net.bestia.bus

/** Nobody was subscribed to the login command channel: the login server is down or still connecting. */
class LoginUnavailableException : RuntimeException("No login server is listening for commands")
