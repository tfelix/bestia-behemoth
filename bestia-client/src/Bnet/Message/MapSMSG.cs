using Godot;

namespace BestiaBehemothClient.Bnet.Message
{
  /// <summary>
  /// A message about the world's terrain, handled by <c>ChunkStreamManager</c> rather than by
  /// <c>ConnectionManager</c>.
  /// </summary>
  /// <remarks>
  /// A marker with no members, and it exists for exactly one reason: <c>BnetSocket.MessageReceived</c> is a
  /// signal, so every subscriber sees every message. <c>ChunkStreamManager</c> subscribes to it in code while
  /// <c>ConnectionManager.tscn</c> wires the same signal to a GDScript handler whose final <c>else</c> reports
  /// anything it does not recognise. Without a common base, that handler needs one branch per terrain message
  /// and grows a new one every time the map protocol does - and until it does, the log fills with
  /// "message was not identified" for traffic that was in fact handled perfectly.
  ///
  /// <para>
  /// <c>[GlobalClass]</c> so GDScript can name the type in an <c>is</c> test.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public abstract partial class MapSMSG : ISMSG
  {
  }
}
