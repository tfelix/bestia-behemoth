package net.bestia.zone.script

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.ScriptArgsProto
import net.bestia.zone.geometry.Vec3L

/**
 * The named values a client gathered before asking for a script to run: where a thing goes, which way it
 * faces, what was clicked.
 *
 * ### Why the accessors return null rather than throwing
 *
 * Every key here came off the wire, so a missing one and a wrongly typed one are both just "the client did
 * not send what this script needs" - a refusal, not an exception. A script reads what it needs and returns
 * false if it is not there, which is the same shape as every other check it makes.
 *
 * ### Keys are constants, not literals
 *
 * See [ScriptArgKeys]. A bag gives up the compiler's help with spelling; naming every key in one place is
 * what buys it back.
 */
class ScriptArgs private constructor(private val byKey: Map<String, Any>) {

  val isEmpty: Boolean
    get() {
      return byKey.isEmpty()
    }

  fun vec(key: String): Vec3L? {
    return byKey[key] as? Vec3L
  }

  fun long(key: String): Long? {
    return byKey[key] as? Long
  }

  fun double(key: String): Double? {
    return byKey[key] as? Double
  }

  /** The common case for an angle or a scale, where the wire's precision is far more than anyone needs. */
  fun float(key: String): Float? {
    return double(key)?.toFloat()
  }

  fun text(key: String): String? {
    return byKey[key] as? String
  }

  fun bool(key: String): Boolean? {
    return byKey[key] as? Boolean
  }

  override fun toString(): String {
    return "ScriptArgs($byKey)"
  }

  companion object {
    /**
     * How much of a bag is honest.
     *
     * A script reads a handful of named values; anything past that is either a bug or someone probing what
     * the server will hold onto. The excess is dropped rather than refused, so a client that grows a new
     * argument against an older server degrades to "that script did not get what it wanted".
     */
    const val MAX_ARGS = 16
    const val MAX_KEY_LENGTH = 64
    const val MAX_TEXT_LENGTH = 256

    val EMPTY = ScriptArgs(emptyMap())

    /**
     * Reads a client's bag. **This is the one place untrusted keys enter**, hence the caps above.
     *
     * A `VALUE_NOT_SET` arg is dropped: proto3 cannot distinguish "the oneof was never set" from a default,
     * so an entry with no value is not a false or a zero, it is nothing.
     */
    fun fromBnet(proto: ScriptArgsProto.ScriptArgs): ScriptArgs {
      if (proto.argsCount == 0) {
        return EMPTY
      }

      if (proto.argsCount > MAX_ARGS) {
        LOG.warn { "Dropping ${proto.argsCount - MAX_ARGS} script args past the cap of $MAX_ARGS" }
      }

      val byKey = proto.argsList.asSequence()
        .take(MAX_ARGS)
        .filter { it.key.isNotEmpty() && it.key.length <= MAX_KEY_LENGTH }
        .mapNotNull { arg -> valueOf(arg)?.let { arg.key to it } }
        .toMap()

      return ScriptArgs(byKey)
    }

    /** Builds a bag directly, for a caller that already holds real values - tests and server-side scripts. */
    fun of(vararg args: Pair<String, Any>): ScriptArgs {
      args.forEach { (key, value) ->
        require(value is Long || value is Double || value is String || value is Vec3L || value is Boolean) {
          "Script arg '$key' is a ${value::class.simpleName}, which has no wire representation"
        }
      }

      return ScriptArgs(args.toMap())
    }

    private fun valueOf(arg: ScriptArgsProto.ScriptArg): Any? {
      return when (arg.valueCase) {
        ScriptArgsProto.ScriptArg.ValueCase.INT_VALUE -> arg.intValue
        ScriptArgsProto.ScriptArg.ValueCase.FLOAT_VALUE -> arg.floatValue
        ScriptArgsProto.ScriptArg.ValueCase.TEXT_VALUE -> arg.textValue.take(MAX_TEXT_LENGTH)
        ScriptArgsProto.ScriptArg.ValueCase.VEC_VALUE -> Vec3L(arg.vecValue.x, arg.vecValue.y, arg.vecValue.z)
        ScriptArgsProto.ScriptArg.ValueCase.BOOL_VALUE -> arg.boolValue
        ScriptArgsProto.ScriptArg.ValueCase.VALUE_NOT_SET, null -> null
      }
    }

    private val LOG = KotlinLogging.logger { }
  }
}
