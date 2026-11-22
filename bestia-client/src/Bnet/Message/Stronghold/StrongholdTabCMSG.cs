using Bnet;

namespace BestiaBehemothClient.Bnet.Message.Stronghold
{
  /// <summary>The guild window's Stronghold tab; answered with a <see cref="StrongholdTabSMSG"/>.</summary>
  public partial class StrongholdTabCMSG : ICMSG
  {
    /// <summary>The proto's StrongholdTabAction.</summary>
    public int Action { get; set; }

    public uint SeedRank { get; set; }

    public override Envelope ToEnvelope()
    {
      return new Envelope
      {
        StrongholdTabRequest = new global::Bnet.StrongholdTabCMSG
        {
          Action = (global::Bnet.StrongholdTabAction)Action,
          SeedRank = SeedRank
        }
      };
    }
  }
}
