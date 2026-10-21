using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>Invites another player into the guild by master name.</summary>
  public partial class InviteToGuildCMSG : ICMSG
  {
    public string MasterName { get; set; } = string.Empty;

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        InviteToGuild = new global::Bnet.InviteToGuildCMSG
        {
          MasterName = MasterName
        }
      };
    }
  }
}
