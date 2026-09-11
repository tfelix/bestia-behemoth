using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Map
{
  /// <summary>
  /// "I clicked on that." What the click means is decided server-side, from what the target is.
  /// </summary>
  /// <remarks>
  /// The general form of a world interaction, where <see cref="CollectPropCMSG"/> and AttackEntityCMSG are
  /// the specific ones. Ids here are live ECS ids and only valid while the client holds the thing - sending a
  /// stale one is harmless, since the server cannot resolve it and refuses.
  /// </remarks>
  public partial class InteractEntityCMSG : ICMSG
  {
    public long EntityId { get; set; }

    /// <summary>Whatever the interaction needed the player to choose first. Null for a plain click.</summary>
    public Message.ScriptArgs Args { get; set; }

    public override Envelope ToEnvelope()
    {
      var interact = new global::Bnet.InteractEntityCMSG
      {
        EntityId = (ulong)EntityId,
        Args = Args?.ToProto()
      };

      return new Envelope
      {
        InteractEntity = interact
      };
    }
  }
}
