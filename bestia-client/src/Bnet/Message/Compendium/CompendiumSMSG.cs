using Godot;

namespace BestiaBehemothClient.Bnet.Message.Compendium
{
  /// <summary>Every species the selected master has seen, sent whole on request and after every change.</summary>
  [GlobalClass]
  public partial class CompendiumSMSG : ISMSG
  {
    [Export] public Godot.Collections.Array<CompendiumEntry> Entries { get; set; } = [];

    public static CompendiumSMSG FromProto(global::Bnet.CompendiumSMSG proto)
    {
      var msg = new CompendiumSMSG();
      foreach (var entry in proto.Entries)
      {
        msg.Entries.Add(CompendiumEntry.FromProto(entry));
      }

      return msg;
    }
  }
}
