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

    /// <summary>Empty at a start town's house, which has no owner.</summary>
    [Export] public string OwnerName { get; set; } = "";

    [Export] public int OwnerFeePercent { get; set; }

    /// <summary>Whether this player owns the house; the fields below are only filled for the owner.</summary>
    [Export] public bool IsOwner { get; set; }

    [Export] public int MaxOwnerFeePercent { get; set; }
    [Export] public long Till { get; set; }

    /// <summary>When the fee may change again; 0 when it may now.</summary>
    [Export] public long FeeChangeableAtEpochSeconds { get; set; }

    [Export] public bool CanTearDown { get; set; }

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
        LinkedTownNames = linked,
        OwnerName = proto.OwnerName,
        OwnerFeePercent = proto.OwnerFeePercent,
        IsOwner = proto.IsOwner,
        MaxOwnerFeePercent = proto.MaxOwnerFeePercent,
        Till = proto.Till,
        FeeChangeableAtEpochSeconds = proto.FeeChangeableAtEpochSeconds,
        CanTearDown = proto.CanTearDown
      };
    }
  }
}
