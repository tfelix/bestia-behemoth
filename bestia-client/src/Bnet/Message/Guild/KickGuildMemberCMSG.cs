using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>Removes a member of a lower rank from the guild and expels it, giving a reason.</summary>
  public partial class KickGuildMemberCMSG : ICMSG
  {
    public ulong MasterId { get; set; }

    public string Reason { get; set; } = string.Empty;

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        KickGuildMember = new global::Bnet.KickGuildMemberCMSG
        {
          MasterId = MasterId,
          Reason = Reason
        }
      };
    }
  }
}
