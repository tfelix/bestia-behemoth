using Godot;

namespace BestiaBehemothClient.Bnet.Message.Crafting
{
  /// <summary>Another master's Bestia, offered for etching at the Carving Table the window is open at.</summary>
  [GlobalClass]
  public partial class EtchingOffer : RefCounted
  {
    [Export] public ulong EntityId { get; set; }
    [Export] public uint MobId { get; set; }
    [Export] public uint Level { get; set; }
    [Export] public string OwnerName { get; set; } = "";

    public static EtchingOffer FromProto(global::Bnet.EtchingOffer proto)
    {
      return new EtchingOffer
      {
        EntityId = proto.EntityId,
        MobId = proto.MobId,
        Level = proto.Level,
        OwnerName = proto.OwnerName
      };
    }
  }
}
