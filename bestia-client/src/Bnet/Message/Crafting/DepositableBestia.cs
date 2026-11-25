using Godot;

namespace BestiaBehemothClient.Bnet.Message.Crafting
{
  /// <summary>One of the player's own Bestias that could be locked into the Carving Table.</summary>
  [GlobalClass]
  public partial class DepositableBestia : RefCounted
  {
    [Export] public ulong PlayerBestiaId { get; set; }
    [Export] public uint MobId { get; set; }
    [Export] public int Level { get; set; }

    /// <summary>The name its master gave it, or empty for the species name.</summary>
    [Export] public string Name { get; set; } = string.Empty;

    /// <summary>In storage rather than called out; locking it in needs the storage to be open.</summary>
    [Export] public bool InStorage { get; set; }

    public static DepositableBestia FromProto(global::Bnet.DepositableBestia proto)
    {
      return new DepositableBestia
      {
        PlayerBestiaId = proto.PlayerBestiaId,
        MobId = proto.MobId,
        Level = (int)proto.Level,
        Name = proto.Name,
        InStorage = proto.InStorage
      };
    }
  }
}
