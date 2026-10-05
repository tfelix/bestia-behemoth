using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Party
{
  /// <summary>The party owner disbands the party.</summary>
  public partial class RequestDisbandPartyCMSG : ICMSG
  {
    public ulong PartyId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        RequestDisbandParty = new global::Bnet.RequestDisbandPartyCMSG
        {
          PartyId = PartyId
        }
      };
    }
  }
}
