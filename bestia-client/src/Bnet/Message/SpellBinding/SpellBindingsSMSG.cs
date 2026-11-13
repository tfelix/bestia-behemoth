using Godot;

namespace BestiaBehemothClient.Bnet.Message.SpellBinding
{
  /// <summary>The master's own Spell Bindings, sent on request and after each change.</summary>
  [GlobalClass]
  public partial class SpellBindingsSMSG : ISMSG
  {
    [Export] public Godot.Collections.Array<SpellBindingEntry> Bindings { get; set; } = [];

    public static SpellBindingsSMSG FromProto(global::Bnet.SpellBindingsSMSG proto)
    {
      var bindings = new Godot.Collections.Array<SpellBindingEntry>();
      foreach (var binding in proto.Bindings)
      {
        bindings.Add(SpellBindingEntry.FromProto(binding));
      }

      return new SpellBindingsSMSG { Bindings = bindings };
    }
  }
}
