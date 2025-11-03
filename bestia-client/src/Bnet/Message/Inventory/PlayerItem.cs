using Godot;

namespace BestiaBehemothClient.Bnet.Message.Inventory
{
  /// <summary>
  /// Represents a single item in a player's inventory
  /// </summary>
  [GlobalClass]
  public partial class PlayerItem : GodotObject
  {
    [Export]
    public uint ItemId { get; set; }

    [Export]
    public ulong PlayerItemId { get; set; }

    [Export]
    public uint Amount { get; set; }

    /// <summary>
    /// Creates a PlayerItem from protobuf data
    /// </summary>
    public static PlayerItem FromProto(global::Bnet.PlayerItem protoItem)
    {
      return new PlayerItem
      {
        ItemId = protoItem.ItemId,
        PlayerItemId = protoItem.PlayerItemId,
        Amount = protoItem.Amount
      };
    }
  }
}
