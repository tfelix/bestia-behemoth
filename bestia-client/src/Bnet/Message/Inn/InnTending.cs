using Godot;

namespace BestiaBehemothClient.Bnet.Message.Inn
{
  /// <summary>One bestia the inn would tend, and what that costs.</summary>
  [GlobalClass]
  public partial class InnTending : GodotObject
  {
    [Export] public ulong EntityId { get; set; }

    /// <summary>The bestia's own name; empty for one without, which goes by its species.</summary>
    [Export] public string Name { get; set; } = "";
    [Export] public ulong BestiaId { get; set; }
    [Export] public int Price { get; set; }
    [Export] public bool Fallen { get; set; }

    public static InnTending FromProto(global::Bnet.InnSMSG.Types.Tending proto)
    {
      return new InnTending
      {
        EntityId = proto.EntityId,
        Name = proto.Name,
        BestiaId = proto.BestiaId,
        Price = (int)proto.Price,
        Fallen = proto.Fallen,
      };
    }
  }
}
