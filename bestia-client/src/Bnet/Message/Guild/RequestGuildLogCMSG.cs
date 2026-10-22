using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>Asks for the guild's newest log entries; answered with a <see cref="GuildLogSMSG"/>.</summary>
  public partial class RequestGuildLogCMSG : ICMSG
  {
    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        RequestGuildLog = new global::Bnet.RequestGuildLogCMSG()
      };
    }
  }
}
