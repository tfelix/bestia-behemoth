package net.bestia.testclient

import net.bestia.testclient.commands.LoginCommand
import java.util.concurrent.LinkedBlockingQueue

fun main() {
  println("Bestia CLI Client")

  val ip = "localhost"
  val port = 8990

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

