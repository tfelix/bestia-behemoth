using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Map
{
  /// <summary>
  /// Asks to take one static prop straight into the inventory.
  /// </summary>
  /// <remarks>
  /// The id comes off a <see cref="ChunkStaticEntitiesSMSG.Entry.EntityId"/> and is only valid while the
  /// chunk is held - see that class's note. Sending a stale one is harmless: the server cannot resolve it and
  /// refuses, which is what makes it safe for the pick proxy to carry an id it may have held for a while.
  /// </remarks>
  public partial class CollectPropCMSG : ICMSG
  {
    public long EntityId { get; set; }

    public override Envelope ToEnvelope()
    {
      var collectProp = new global::Bnet.CollectPropCMSG
      {
        EntityId = (ulong)EntityId
      };

      return new Envelope
      {
        CollectProp = collectProp
      };
    }
  }
}
