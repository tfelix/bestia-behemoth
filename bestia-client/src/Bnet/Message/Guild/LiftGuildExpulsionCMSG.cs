using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>Lets an expelled master be invited into the guild again.</summary>
  public partial class LiftGuildExpulsionCMSG : ICMSG
  {
    public ulong MasterId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        LiftGuildExpulsion = new global::Bnet.LiftGuildExpulsionCMSG
        {
          MasterId = MasterId
        }
      };
    }
  }
}
