using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>Asks for the orders of one of the player's bestias.</summary>
  public partial class GetBestiaOrdersCMSG : ICMSG
  {
    public ulong PlayerBestiaId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope { GetBestiaOrders = new global::Bnet.GetBestiaOrdersCMSG { PlayerBestiaId = PlayerBestiaId } };
    }
  }
}
