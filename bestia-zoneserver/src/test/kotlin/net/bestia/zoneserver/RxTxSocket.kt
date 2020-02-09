package net.bestia.zoneserver

import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class RxTxSocket(
    ip: String,
    port: Int
) : Closeable {
  private val socket: Socket = Socket(ip, port)
  private val dOut: DataOutputStream
  private val dIn: DataInputStream

  private val sendRunnable: SendRunnable
  private val txThread: Thread
  private var isRunning = true

  private val inputBuffer = LinkedBlockingQueue<ByteArray>()

  init {
    dOut = DataOutputStream(socket.getOutputStream())
    dIn = DataInputStream(socket.getInputStream())

    sendRunnable = SendRunnable()
    txThread = Thread(sendRunnable)

    txThread.start()
  }

  private inner class SendRunnable : Runnable {
    override fun run() {
      while (isRunning) {
        try {
          sendData()
          receiveData()
        } catch (e: InterruptedException) {
          close()
        }
      }
    }
  }

  private fun receiveData() {
    var available = dIn.available()
    if (available <= 4) {
      return
    }

    val packetSize = dIn.readInt()
    if (packetSize < dIn.available()) {
      dIn.reset()
      return
    }

    val buffer = ByteArray(packetSize)
    dIn.read(buffer, 0, packetSize)

    println(buffer)
  }

  private fun sendData() {
    val element = inputBuffer.poll(1000, TimeUnit.MILLISECONDS)
        ?: return

    val sendBuffer = ByteBuffer.allocate(element.size + Int.SIZE_BYTES)
    sendBuffer.putInt(element.size)
    sendBuffer.put(element)

    try {
      dOut.write(sendBuffer.array())
      dOut.flush()
    } catch (e: IOException) {
      System.err.println(e.message)
      isRunning = false
    }
  }

  fun send(data: ByteArray) {
    inputBuffer.add(data)
  }

  override fun close() {
    dOut.close()
    dIn.close()
    socket.close()
    isRunning = false
    txThread.interrupt()
  }

  fun join() {
    txThread.join()
  }
}