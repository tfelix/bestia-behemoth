package testclient

import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

class PrintRunnable(
    val outputQueue: BlockingQueue<String>
) : Runnable {

  private var isRunning = true

  fun stop() {
    isRunning = false
    outputQueue.put("")
  }

  override fun run() {
    while (isRunning) {
      val out = outputQueue.poll(1000, TimeUnit.MILLISECONDS)
      if(!out.isNullOrEmpty()) {
        println(out)
      }
    }
  }
}