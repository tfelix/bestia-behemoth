package net.bestia.bus

import net.bestia.bus.contract.LoginCommand
import net.bestia.bus.contract.LoginReply

/** The login server's answer to one command. Runs on a virtual thread; it may block. */
fun interface LoginCommandHandler {
  fun handle(actorAccountId: Long, command: LoginCommand): LoginReply.Outcome
}
