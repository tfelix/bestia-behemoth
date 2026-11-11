using Godot;

namespace BestiaBehemothClient.Bnet.Message.SpellScroll
{
  /// <summary>The attack on each spell scroll the client asked about. A scroll that holds none is left out.</summary>
  [GlobalClass]
  public partial class SpellScrollsSMSG : ISMSG
  {
    [Export] public Godot.Collections.Array<SpellScrollEntry> Scrolls { get; set; } = [];

    public static SpellScrollsSMSG FromProto(global::Bnet.SpellScrollsSMSG proto)
    {
      var scrolls = new Godot.Collections.Array<SpellScrollEntry>();
      foreach (var scroll in proto.Scrolls)
      {
        scrolls.Add(SpellScrollEntry.FromProto(scroll));
      }

      return new SpellScrollsSMSG { Scrolls = scrolls };
    }
  }
}
