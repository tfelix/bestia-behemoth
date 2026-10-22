using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Guild
{
  /// <summary>Edits a rank, or adds one at the bottom with <see cref="RankId"/> 0.</summary>
  public partial class SaveGuildRankCMSG : ICMSG
  {
    public ulong RankId { get; set; }

    public string Name { get; set; } = string.Empty;

    public uint TaxPercent { get; set; }

    public bool CanInvite { get; set; }

    public bool CanKick { get; set; }

    public bool CanManageRanks { get; set; }

    public bool CanUseGuildSkills { get; set; }

    public bool CanEditMotd { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        SaveGuildRank = new global::Bnet.SaveGuildRankCMSG
        {
          RankId = RankId,
          Name = Name,
          TaxPercent = TaxPercent,
          CanInvite = CanInvite,
          CanKick = CanKick,
          CanManageRanks = CanManageRanks,
          CanUseGuildSkills = CanUseGuildSkills,
          CanEditMotd = CanEditMotd
        }
      };
    }
  }
}
