package net.bestia.testclient

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue

class RxTxSocket(
    private val printRunnable: PrintRunnable,
    ip: String,
    port: Int
) {
  private val socket: Socket = Socket(ip, port)
  private val dOut: DataOutputStream
  private val dIn: DataInputStream

  private val rxThread: Thread
  private val txThread: Thread

  private val inputBuffer = ConcurrentLinkedQueue<ByteArray>()

  init {
    dOut = DataOutputStream(socket.getOutputStream())
    dIn = DataInputStream(socket.getInputStream())

    rxThread = Thread(SendThread())
    txThread = Thread(ReceiveThread())
  }

  private inner class SendThread : Runnable {
    override fun run() {
      val element = inputBuffer.poll()
      if(element != null) {
        val sendBuffer = ByteBuffer.allocate(element.size + Int.SIZE_BYTES)
        sendBuffer.putInt(element.size)
        sendBuffer.put(element)

        dOut.write(element)
        dOut.flush()
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
  }

  fun join() {
    rxThread.join()
    txThread.join()
  }
}