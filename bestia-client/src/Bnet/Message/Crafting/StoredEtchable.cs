using Godot;

namespace BestiaBehemothClient.Bnet.Message.Crafting
{
  /// <summary>One of the Artificer's own Bestias in storage, which Runic Etching may take from there.</summary>
  [GlobalClass]
  public partial class StoredEtchable : RefCounted
  {
    [Export] public ulong PlayerBestiaId { get; set; }
    [Export] public uint MobId { get; set; }
    [Export] public int Level { get; set; }

    /// <summary>The name its master gave it, or empty for the species name.</summary>
    [Export] public string Name { get; set; } = string.Empty;

    public static StoredEtchable FromProto(global::Bnet.StoredEtchable proto)
    {
      return new StoredEtchable
      {
        PlayerBestiaId = proto.PlayerBestiaId,
        MobId = proto.MobId,
        Level = (int)proto.Level,
        Name = proto.Name
      };
    }
  }
}
