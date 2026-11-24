using BestiaBehemothClient.Bnet.Message.Master;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.BestiaStorage
{
  /// <summary>One bestia in the storage, with its pools as it went in.</summary>
  [GlobalClass]
  public partial class StoredBestia : GodotObject
  {
    [Export] public BestiaInfo Info { get; set; }

    /// <summary>Until it may be summoned again; 0 when it may now.</summary>
    [Export] public int RestingSeconds { get; set; }

    [Export] public int Health { get; set; }
    [Export] public int MaxHealth { get; set; }
    [Export] public int Mana { get; set; }
    [Export] public int MaxMana { get; set; }
    [Export] public int Stamina { get; set; }
    [Export] public int MaxStamina { get; set; }

    public static StoredBestia FromProto(global::Bnet.BestiaStorageSMSG.Types.StoredBestia proto)
    {
      return new StoredBestia
      {
        Info = BestiaInfo.FromProto(proto.Info),
        RestingSeconds = (int)proto.RestingSeconds,
        Health = (int)proto.Health.Current,
        MaxHealth = (int)proto.Health.Max,
        Mana = (int)proto.Mana.Current,
        MaxMana = (int)proto.Mana.Max,
        Stamina = (int)proto.Stamina.Current,
        MaxStamina = (int)proto.Stamina.Max,
      };
    }
  }
}
