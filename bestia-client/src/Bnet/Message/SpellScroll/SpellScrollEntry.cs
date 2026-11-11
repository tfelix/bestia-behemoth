using Godot;

namespace BestiaBehemothClient.Bnet.Message.SpellScroll
{
  /// <summary>The attack one spell scroll holds.</summary>
  /// <remarks>Its own file because Godot registers one <c>[GlobalClass]</c> per C# file, see <c>ShopEntry</c>.</remarks>
  [GlobalClass]
  public partial class SpellScrollEntry : GodotObject
  {
    [Export] public ulong UniqueId { get; set; }
    [Export] public ulong SkillId { get; set; }
    [Export] public int Level { get; set; }

    public static SpellScrollEntry FromProto(global::Bnet.SpellScroll proto)
    {
      return new SpellScrollEntry { UniqueId = proto.UniqueId, SkillId = proto.SkillId, Level = proto.Level };
    }
  }
}
