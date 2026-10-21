using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>Moves a member of a lower rank onto another rank.</summary>
  public partial class SetGuildMemberRankCMSG : ICMSG
  {
    public ulong MasterId { get; set; }

    public ulong RankId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        SetGuildMemberRank = new global::Bnet.SetGuildMemberRankCMSG
        {
          MasterId = MasterId,
          RankId = RankId
        }
      };
    }
  }
}
