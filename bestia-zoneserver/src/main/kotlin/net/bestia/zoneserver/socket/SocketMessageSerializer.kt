package net.bestia.zoneserver.socket

interface SocketMessageSerializer<T> {
  fun write(): ByteArray
  fun read(data: ByteArray): T
}