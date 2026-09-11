package net.bestia.zone.script

import net.bestia.bnet.proto.ScriptArgsProto
import net.bestia.bnet.proto.Vec3OuterClass
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [ScriptArgs.fromBnet] is the one place client-chosen keys enter the server, so the caps are the point of
 * this class rather than a detail of it.
 */
class ScriptArgsTest {

  @Test
  fun `every value type survives the round trip`() {
    val args = ScriptArgs.fromBnet(
      bag(
        arg("i") { it.intValue = 42L },
        arg("f") { it.floatValue = 1.5 },
        arg("t") { it.textValue = "hello" },
        arg("v") { it.vecValue = vec(1, 2, 3) },
        arg("b") { it.boolValue = true }
      )
    )

    assertEquals(42L, args.long("i"))
    assertEquals(1.5, args.double("f"))
    assertEquals(1.5f, args.float("f"))
    assertEquals("hello", args.text("t"))
    assertEquals(Vec3L(1, 2, 3), args.vec("v"))
    assertEquals(true, args.bool("b"))
  }

  @Test
  fun `reading a key as the wrong type is a miss, not a crash`() {
    val args = ScriptArgs.fromBnet(bag(arg("position") { it.intValue = 7L }))

    assertNull(args.vec("position"))
    assertNull(args.text("position"))
    assertEquals(7L, args.long("position"))
  }

  @Test
  fun `an absent key is null`() {
    assertNull(ScriptArgs.EMPTY.vec(ScriptArgKeys.POSITION))
    assertTrue(ScriptArgs.EMPTY.isEmpty)
  }

  @Test
  fun `an empty bag is the shared empty instance`() {
    assertSame(ScriptArgs.EMPTY, ScriptArgs.fromBnet(bag()))
  }

  @Test
  fun `args past the cap are dropped rather than refused`() {
    val tooMany = (0..ScriptArgs.MAX_ARGS).map { i -> arg("k$i") { it.intValue = i.toLong() } }

    val args = ScriptArgs.fromBnet(bag(*tooMany.toTypedArray()))

    assertEquals(0L, args.long("k0"), "the first args are kept")
    assertNull(args.long("k${ScriptArgs.MAX_ARGS}"), "the one past the cap is gone")
  }

  @Test
  fun `an over-long key is dropped and an over-long text is truncated`() {
    val args = ScriptArgs.fromBnet(
      bag(
        arg("k".repeat(ScriptArgs.MAX_KEY_LENGTH + 1)) { it.intValue = 1L },
        arg("t") { it.textValue = "x".repeat(ScriptArgs.MAX_TEXT_LENGTH + 10) }
      )
    )

    assertTrue(args.long("k".repeat(ScriptArgs.MAX_KEY_LENGTH + 1)) == null)
    assertEquals(ScriptArgs.MAX_TEXT_LENGTH, args.text("t")?.length)
  }

  @Test
  fun `an arg whose value was never set is not a zero`() {
    val args = ScriptArgs.fromBnet(bag(ScriptArgsProto.ScriptArg.newBuilder().setKey("empty").build()))

    assertNull(args.long("empty"))
    assertNull(args.bool("empty"))
    assertTrue(args.isEmpty)
  }

  private fun bag(vararg args: ScriptArgsProto.ScriptArg): ScriptArgsProto.ScriptArgs {
    return ScriptArgsProto.ScriptArgs.newBuilder().addAllArgs(args.toList()).build()
  }

  private fun arg(key: String, value: (ScriptArgsProto.ScriptArg.Builder) -> Unit): ScriptArgsProto.ScriptArg {
    val builder = ScriptArgsProto.ScriptArg.newBuilder().setKey(key)
    value(builder)

    return builder.build()
  }

  private fun vec(x: Long, y: Long, z: Long): Vec3OuterClass.Vec3 {
    return Vec3OuterClass.Vec3.newBuilder().setX(x).setY(y).setZ(z).build()
  }
}
