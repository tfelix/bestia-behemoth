using Godot;

namespace BestiaBehemothClient.Bnet.Message.Crafting
{
  /// <summary>A Bestia locked into the Carving Table the window is open at, any master's.</summary>
  [GlobalClass]
  public partial class DepositedBestia : RefCounted
  {
    [Export] public ulong PlayerBestiaId { get; set; }
    [Export] public uint MobId { get; set; }
    [Export] public int Level { get; set; }

    /// <summary>The name its master gave it, or empty for the species name.</summary>
    [Export] public string Name { get; set; } = string.Empty;

    [Export] public string OwnerName { get; set; } = string.Empty;

    /// <summary>The player owns it and may take it back.</summary>
    [Export] public bool Mine { get; set; }

    /// <summary>How long the player would take to etch it, in seconds.</summary>
    [Export] public int EtchSeconds { get; set; }

    /// <summary>Seconds left of the etching under way on it; 0 when none is.</summary>
    [Export] public int EtchSecondsLeft { get; set; }

    [Export] public string EtcherName { get; set; } = string.Empty;

    /// <summary>The player is the one etching it and may call it off.</summary>
    [Export] public bool EtchingMine { get; set; }

    public static DepositedBestia FromProto(global::Bnet.DepositedBestia proto)
    {
      return new DepositedBestia
      {
        PlayerBestiaId = proto.PlayerBestiaId,
        MobId = proto.MobId,
        Level = (int)proto.Level,
        Name = proto.Name,
        OwnerName = proto.OwnerName,
        Mine = proto.Mine,
        EtchSeconds = (int)proto.EtchSeconds,
        EtchSecondsLeft = (int)proto.EtchSecondsLeft,
        EtcherName = proto.EtcherName,
        EtchingMine = proto.EtchingMine
      };
    }
  }
}
