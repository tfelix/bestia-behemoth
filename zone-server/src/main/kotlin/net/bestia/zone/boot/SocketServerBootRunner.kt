package net.bestia.zone.boot

import net.bestia.zone.socket.SocketServer
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@Profile("!no-socket")
class SocketServerBootRunner(
  private val socketServer: SocketServer
) : ApplicationRunner {
  override fun run(args: ApplicationArguments?) {
    socketServer.start()
  }
}