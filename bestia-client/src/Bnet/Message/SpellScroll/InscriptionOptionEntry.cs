using Godot;

namespace BestiaBehemothClient.Bnet.Message.SpellScroll
{
  /// <summary>One attack Spell Inscription could copy off one of the master's bestias, with the odds.</summary>
  /// <remarks>Its own file because Godot registers one <c>[GlobalClass]</c> per C# file, see <c>ShopEntry</c>.</remarks>
  [GlobalClass]
  public partial class InscriptionOptionEntry : GodotObject
  {
    [Export] public ulong BestiaEntityId { get; set; }
    [Export] public ulong SkillId { get; set; }
    [Export] public int Level { get; set; }
    [Export] public int SuccessPercent { get; set; }
    [Export] public int MindBreakPercent { get; set; }

    public static InscriptionOptionEntry FromProto(global::Bnet.InscriptionOption proto)
    {
      return new InscriptionOptionEntry
      {
        BestiaEntityId = proto.BestiaEntityId,
        SkillId = proto.SkillId,
        Level = proto.Level,
        SuccessPercent = proto.SuccessPercent,
        MindBreakPercent = proto.MindBreakPercent
      };
    }
  }
}
