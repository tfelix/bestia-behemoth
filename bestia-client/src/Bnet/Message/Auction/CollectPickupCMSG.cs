using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Auction
{
  /// <summary>Takes a waiting purchase at the house it waits at.</summary>
  public partial class CollectPickupCMSG : ICMSG
  {
    public ulong PickupId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        CollectPickup = new global::Bnet.CollectPickupCMSG
        {
          PickupId = PickupId
        }
      };
    }
  }
}
