using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Notebook
{
  /// <summary>Asks for every entry of one notebook collection; answered with a <see cref="NotebookSMSG"/>.</summary>
  public partial class NotebookListCMSG : ICMSG
  {
    [Export] public string Collection { get; set; } = "";

    public override Envelope ToEnvelope()
    {
      return new Envelope { NotebookList = new global::Bnet.NotebookListCMSG { Collection = Collection } };
    }
  }
}
