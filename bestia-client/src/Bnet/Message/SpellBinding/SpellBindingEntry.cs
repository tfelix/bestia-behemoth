using Godot;

namespace BestiaBehemothClient.Bnet.Message.SpellBinding
{
  /// <summary>One of the master's own Spell Bindings that waits for its trigger.</summary>
  /// <remarks>Its own file because Godot registers one <c>[GlobalClass]</c> per C# file, see <c>ShopEntry</c>.</remarks>
  [GlobalClass]
  public partial class SpellBindingEntry : GodotObject
  {
    [Export] public ulong BindingId { get; set; }
    [Export] public ulong SkillId { get; set; }
    [Export] public int Level { get; set; }
    [Export] public int Trigger { get; set; }
    [Export] public int Filter { get; set; }
    [Export] public bool OnGround { get; set; }
    [Export] public Vector3 Position { get; set; }
    [Export] public ulong ItemUniqueId { get; set; }

    public static SpellBindingEntry FromProto(global::Bnet.SpellBinding proto)
    {
      return new SpellBindingEntry
      {
        BindingId = proto.BindingId,
        SkillId = proto.SkillId,
        Level = proto.Level,
        Trigger = proto.Trigger,
        Filter = proto.Filter,
        OnGround = proto.Position != null,
        Position = proto.Position != null ? new Vector3(proto.Position.X, proto.Position.Z, proto.Position.Y) : Vector3.Zero,
        ItemUniqueId = proto.ItemUniqueId,
      };
    }
  }
}
