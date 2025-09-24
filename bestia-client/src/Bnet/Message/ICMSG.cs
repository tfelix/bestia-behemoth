using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message
{
  /// <summary>
  /// Interface for message objects that are coming from the client and are ment to
  /// be send to the server (c = client)
  /// </summary>
  public abstract partial class ICMSG : GodotObject
  {
    /// <summary>
    /// Converts the message to a protobuf Envelope for network transmission
    /// </summary>
    /// <returns>The Envelope containing this message</returns>
    public abstract Envelope ToEnvelope();
  }
}