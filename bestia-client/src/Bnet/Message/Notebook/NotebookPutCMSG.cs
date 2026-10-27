using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Notebook
{
  /// <summary>
  /// Keeps <see cref="Json"/> under <see cref="Key"/>, or under a new key when it is empty. A negative
  /// <see cref="BaseRevision"/> lets the last write win; otherwise the write lands only while the entry is still at it.
  /// </summary>
  public partial class NotebookPutCMSG : ICMSG
  {
    [Export] public string Collection { get; set; } = "";

    [Export] public string Key { get; set; } = "";

    [Export] public string Json { get; set; } = "";

    [Export] public long BaseRevision { get; set; } = -1;

    public override Envelope ToEnvelope()
    {
      var put = new global::Bnet.NotebookPutCMSG { Collection = Collection, Key = Key, Json = Json };
      if (BaseRevision >= 0)
      {
        put.BaseRevision = (ulong)BaseRevision;
      }

      return new Envelope { NotebookPut = put };
    }
  }
}
