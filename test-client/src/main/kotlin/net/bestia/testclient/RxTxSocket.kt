package net.bestia.testclient

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue

class RxTxSocket {
  private lateinit var socket: Socket
  private lateinit var dOut: DataOutputStream
  private lateinit var dIn: DataInputStream
  private lateinit var thread: Thread

  private val inputBuffer = ConcurrentLinkedQueue<ByteArray>()
  val outputBuffer = ConcurrentLinkedQueue<ByteArray>()

  private inner class SendThread : Runnable {
    override fun run() {
      val element = inputBuffer.poll()
      if(element != null) {
        dOut.write(element)
        dOut.flush()
      }

      // dIn.read
    }
  }

  fun send(data: ByteArray) {
    inputBuffer.add(data)
  }

  fun connect(ip: String, port: Int) {
    socket = Socket(ip, port)
    dOut = DataOutputStream(socket.getOutputStream())
    dIn = DataInputStream(socket.getInputStream())
    thread = Thread(SendThread())
  }

  fun close() {
    socket.close()
  }
}