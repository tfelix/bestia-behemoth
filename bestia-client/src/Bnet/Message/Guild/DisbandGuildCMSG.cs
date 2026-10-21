using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>The leader ends the guild; <see cref="ConfirmName"/> is the guild's name as the player typed it.</summary>
  public partial class DisbandGuildCMSG : ICMSG
  {
    public string ConfirmName { get; set; } = string.Empty;

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        DisbandGuild = new global::Bnet.DisbandGuildCMSG
        {
          ConfirmName = ConfirmName
        }
      };
    }
  }
}
