package net.bestia.zoneserver.messages

abstract class MessageConverter<T> {
  abstract fun convertFromBestia(msg: T): ByteArray
  abstract val canConvert: Class<T>
}