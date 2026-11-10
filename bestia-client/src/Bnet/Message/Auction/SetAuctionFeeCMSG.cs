using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Auction
{
  /// <summary>The owner sets the owner fee of their house.</summary>
  public partial class SetAuctionFeeCMSG : ICMSG
  {
    public ulong HouseEntityId { get; set; }

    public int Percent { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        SetAuctionFee = new global::Bnet.SetAuctionFeeCMSG
        {
          HouseEntityId = HouseEntityId,
          Percent = Percent
        }
      };
    }
  }
}
