using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Notebook
{
  /// <summary>Deletes one notebook entry. A negative <see cref="BaseRevision"/> deletes whatever it is now.</summary>
  public partial class NotebookDeleteCMSG : ICMSG
  {
    [Export] public string Collection { get; set; } = "";

    [Export] public string Key { get; set; } = "";

    [Export] public long BaseRevision { get; set; } = -1;

    public override Envelope ToEnvelope()
    {
      var delete = new global::Bnet.NotebookDeleteCMSG { Collection = Collection, Key = Key };
      if (BaseRevision >= 0)
      {
        delete.BaseRevision = (ulong)BaseRevision;
      }

      return new Envelope { NotebookDelete = delete };
    }
  }
}
