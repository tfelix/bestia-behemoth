package net.bestia.testclient

import net.bestia.testclient.commands.Command
import java.util.*

class ReadRunnable(
    private val commands: List<Command>,
    private val socket: RxTxSocket
) : Runnable {
  override fun run() {
    val inScanner = Scanner(System.`in`)
    while(inScanner.hasNext()) {
      val line = inScanner.nextLine()

      if (line.equals("q", true)) {
        break;
      }

      when(val cmd = commands.singleOrNull { it.matches(line) }) {
        null -> println("Unknown Command: $line")
        else -> {
          val data = cmd.execute(line)
          socket.send(data)
        }
      }
    }
  }
}

