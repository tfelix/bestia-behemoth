using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>Asks for this player's guild; answered with a <see cref="GuildInfoSMSG"/>.</summary>
  public partial class RequestGuildInfoCMSG : ICMSG
  {
    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        RequestGuildInfo = new global::Bnet.RequestGuildInfoCMSG()
      };
    }
  }
}
