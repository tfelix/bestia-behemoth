using Godot;

namespace BestiaBehemothClient.Bnet.Message.Notebook
{
  /// <summary>
  /// One notebook entry written or deleted, in this client or in the companion app, and the revision its collection
  /// is now at. <see cref="Json"/> is empty for a deletion.
  /// </summary>
  [GlobalClass]
  public partial class NotebookChangedSMSG : ISMSG
  {
    [Export] public string Collection { get; set; } = "";

    [Export] public string Key { get; set; } = "";

    [Export] public long Revision { get; set; }

    [Export] public bool Deleted { get; set; }

    [Export] public string Json { get; set; } = "";

    public static NotebookChangedSMSG FromProto(global::Bnet.NotebookChangedSMSG proto)
    {
      return new NotebookChangedSMSG
      {
        Collection = proto.Collection,
        Key = proto.Key,
        Revision = (long)proto.Revision,
        Deleted = proto.Deleted,
        Json = proto.Json,
      };
    }
  }
}
