package net.bestia.testclient

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class RxTxSocket(
    private val printRunnable: PrintRunnable,
    ip: String,
    port: Int
) {
  private val socket: Socket = Socket(ip, port)
  private val dOut: DataOutputStream
  private val dIn: DataInputStream

  private val sendRunnable: SendRunnable
  private val rxThread: Thread
  private val txThread: Thread

  private val inputBuffer = LinkedBlockingQueue<ByteArray>()

  init {
    dOut = DataOutputStream(socket.getOutputStream())
    dIn = DataInputStream(socket.getInputStream())

    sendRunnable = SendRunnable()
    txThread = Thread(sendRunnable)
    rxThread = Thread(ReceiveThread())

    txThread.start()
    rxThread.start()
  }

  private inner class SendRunnable : Runnable {
    var isRunning = true

    override fun run() {
      while (isRunning) {
        try {
          val element = inputBuffer.poll(1000, TimeUnit.MILLISECONDS)
              ?: continue

          val sendBuffer = ByteBuffer.allocate(element.size + Int.SIZE_BYTES)
          sendBuffer.putInt(element.size)
          sendBuffer.put(element)

          dOut.write(sendBuffer.array())
          dOut.flush()
        } catch (e: InterruptedException) {
          // no op
        }
      }
    }
  }

  private inner class ReceiveThread : Runnable {
    override fun run() {
      printRunnable.outputQueue.put("output")
    }
  }

  fun send(data: ByteArray) {
    inputBuffer.add(data)
  }

  fun close() {
    socket.close()
    sendRunnable.isRunning = false
    txThread.interrupt()
  }

  fun join() {
    rxThread.join()
    txThread.join()
  }
}