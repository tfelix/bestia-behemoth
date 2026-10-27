using Godot;

namespace BestiaBehemothClient.Bnet.Message.Notebook
{
  /// <summary>
  /// Every entry of one notebook collection, and the revision it is at. Each entry is a dictionary with
  /// <c>key</c>, <c>revision</c> and <c>json</c>, the entry as its collection shows it.
  /// </summary>
  [GlobalClass]
  public partial class NotebookSMSG : ISMSG
  {
    [Export] public string Collection { get; set; } = "";

    [Export] public long Revision { get; set; }

    [Export] public Godot.Collections.Array<Godot.Collections.Dictionary> Entries { get; set; } = new();

    public static NotebookSMSG FromProto(global::Bnet.NotebookSMSG proto)
    {
      var message = new NotebookSMSG { Collection = proto.Collection, Revision = (long)proto.Revision };
      foreach (var entry in proto.Entries)
      {
        message.Entries.Add(new Godot.Collections.Dictionary
        {
          ["key"] = entry.Key,
          ["revision"] = (long)entry.Revision,
          ["json"] = entry.Json,
        });
      }

      return message;
    }
  }
}
