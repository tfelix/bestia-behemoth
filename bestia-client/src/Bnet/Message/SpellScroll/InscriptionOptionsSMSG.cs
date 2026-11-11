using Godot;

namespace BestiaBehemothClient.Bnet.Message.SpellScroll
{
  /// <summary>Every attack Spell Inscription could copy off the master's bestias, with the odds the server rolls.</summary>
  [GlobalClass]
  public partial class InscriptionOptionsSMSG : ISMSG
  {
    [Export] public Godot.Collections.Array<InscriptionOptionEntry> Options { get; set; } = [];

    public static InscriptionOptionsSMSG FromProto(global::Bnet.InscriptionOptionsSMSG proto)
    {
      var options = new Godot.Collections.Array<InscriptionOptionEntry>();
      foreach (var option in proto.Options)
      {
        options.Add(InscriptionOptionEntry.FromProto(option));
      }

      return new InscriptionOptionsSMSG { Options = options };
    }
  }
}
