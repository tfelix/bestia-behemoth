using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Crafting
{
  /// <summary>
  /// Abandon the craft in progress. Carries nothing: an entity crafts at most one thing at a time and
  /// the server knows which. Nothing is refunded because nothing has been spent - a craft consumes its
  /// inputs when it resolves.
  /// </summary>
  public partial class CancelCraftCMSG : ICMSG
  {
    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        CancelCraft = new global::Bnet.CancelCraftCMSG()
      };
    }
  }
}
