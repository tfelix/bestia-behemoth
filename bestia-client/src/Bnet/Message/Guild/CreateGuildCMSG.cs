using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>Founds a guild with a Founder's Seal from the master's bag; the seal is spent by the server.</summary>
  public partial class CreateGuildCMSG : ICMSG
  {
    public string Name { get; set; } = string.Empty;

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        CreateGuild = new global::Bnet.CreateGuildCMSG
        {
          Name = Name
        }
      };
    }
  }
}
