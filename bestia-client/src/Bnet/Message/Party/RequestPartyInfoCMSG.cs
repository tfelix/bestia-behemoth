using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Party
{
  /// <summary>Asks for this player's party; answered with a <see cref="PartyInfoSMSG"/>.</summary>
  public partial class RequestPartyInfoCMSG : ICMSG
  {
    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        RequestPartyInfo = new global::Bnet.RequestPartyInfoCMSG()
      };
    }
  }
}
