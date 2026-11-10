using Godot;

namespace BestiaBehemothClient.Bnet.Message.Auction
{
  /// <summary>One purchase waiting at a linked house.</summary>
  /// <remarks>Its own file because Godot registers one <c>[GlobalClass]</c> per C# file.</remarks>
  [GlobalClass]
  public partial class PickupEntry : GodotObject
  {
    [Export] public ulong PickupId { get; set; }
    [Export] public ulong ItemId { get; set; }
    [Export] public int Amount { get; set; }
    [Export] public string TownName { get; set; } = "";
    [Export] public long ExpiresAtEpochSeconds { get; set; }

    /// <summary>The parcel fee to send it to this master's home post office.</summary>
    [Export] public long PostFee { get; set; }

    public static PickupEntry FromProto(global::Bnet.Pickup proto)
    {
      return new PickupEntry
      {
        PickupId = proto.PickupId,
        ItemId = proto.ItemId,
        Amount = proto.Amount,
        TownName = proto.TownName,
        ExpiresAtEpochSeconds = proto.ExpiresAtEpochSeconds,
        PostFee = proto.PostFee
      };
    }
  }
}
