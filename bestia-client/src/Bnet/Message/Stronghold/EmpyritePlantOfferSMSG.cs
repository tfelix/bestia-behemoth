using Godot;

namespace BestiaBehemothClient.Bnet.Message.Stronghold
{
  /// <summary>Asks a guild member at an Empyrite Seed's sprout whether to plant the Empyrite there.</summary>
  [GlobalClass]
  public partial class EmpyritePlantOfferSMSG : ISMSG
  {
    [Export] public ulong SproutEntityId { get; set; }
    [Export] public string SeedName { get; set; } = "";
    [Export] public ulong Coins { get; set; }
    [Export] public uint SiteMinutes { get; set; }

    public static EmpyritePlantOfferSMSG FromProto(global::Bnet.EmpyritePlantOfferSMSG proto)
    {
      return new EmpyritePlantOfferSMSG
      {
        SproutEntityId = proto.SproutEntityId, SeedName = proto.SeedName, Coins = proto.Coins, SiteMinutes = proto.SiteMinutes
      };
    }
  }
}
