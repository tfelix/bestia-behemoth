package testclient

import net.bestia.testclient.RxTxSocket
import net.bestia.testclient.commands.LoginCommand
import java.util.concurrent.LinkedBlockingQueue

fun main(args: Array<String>) {
  println("Bestia CLI Client")

  val ip = args.toList().getOrElse(1) { "localhost" }
  val port = args.toList().getOrNull(2)?.toInt() ?: 8990

  val commands = listOf(
      LoginCommand()
  )

  val printBuffer = LinkedBlockingQueue<String>()

  val printRunnable = PrintRunnable(printBuffer)
  val printThread = Thread(printRunnable)

  val rxTxSocket = RxTxSocket(printRunnable, ip, port)

  val inputRunnable = ReadRunnable(commands, rxTxSocket)
  val inputTread = Thread(inputRunnable)

  inputTread.start()
  inputTread.join()

  rxTxSocket.close()
  rxTxSocket.join()

  printRunnable.stop()
  printThread.join()
}

