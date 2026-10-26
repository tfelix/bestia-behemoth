using Godot;

namespace BestiaBehemothClient.Bnet.Message.Auction
{
  /// <summary>Opens the auction house window: the answer to clicking a house the player can reach.</summary>
  [GlobalClass]
  public partial class AuctionHouseOpenSMSG : ISMSG
  {
    /// <summary>The building, which every later auction request names.</summary>
    [Export] public ulong HouseEntityId { get; set; }

    [Export] public string TownName { get; set; } = "";

    /// <summary>Every town whose house shares this market, this one included.</summary>
    [Export] public Godot.Collections.Array<string> LinkedTownNames { get; set; } = [];

    public static AuctionHouseOpenSMSG FromProto(global::Bnet.AuctionHouseOpenSMSG proto)
    {
      var linked = new Godot.Collections.Array<string>();
      foreach (var town in proto.LinkedTownNames)
      {
        linked.Add(town);
      }

      return new AuctionHouseOpenSMSG
      {
        HouseEntityId = proto.HouseEntityId,
        TownName = proto.TownName,
        LinkedTownNames = linked
      };
    }
  }
}
