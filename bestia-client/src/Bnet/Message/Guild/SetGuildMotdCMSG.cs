using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>Replaces the guild's message of the day; both empty clears it.</summary>
  public partial class SetGuildMotdCMSG : ICMSG
  {
    public string Title { get; set; } = string.Empty;

    public string Body { get; set; } = string.Empty;

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        SetGuildMotd = new global::Bnet.SetGuildMotdCMSG
        {
          Title = Title,
          Body = Body
        }
      };
    }
  }
}
