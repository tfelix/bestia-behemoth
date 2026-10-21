using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>Deletes an empty rank below the sender's own.</summary>
  public partial class DeleteGuildRankCMSG : ICMSG
  {
    public ulong RankId { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        DeleteGuildRank = new global::Bnet.DeleteGuildRankCMSG
        {
          RankId = RankId
        }
      };
    }
  }
}
