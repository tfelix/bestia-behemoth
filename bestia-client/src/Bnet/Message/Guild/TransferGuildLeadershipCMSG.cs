using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>Hands the guild to another member; only the leader may.</summary>
  public partial class TransferGuildLeadershipCMSG : ICMSG
  {
    public ulong MasterId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        TransferGuildLeadership = new global::Bnet.TransferGuildLeadershipCMSG
        {
          MasterId = MasterId
        }
      };
    }
  }
}
