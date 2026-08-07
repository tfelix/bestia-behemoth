using Bnet;
using Godot;

namespace BestiaBehemothClient.Bnet.Message.Master
{
  /// <summary>
  /// Message for permanently deleting one of the account's masters.
  /// The result is reported back via OperationSuccess (MasterDeleted) / OperationError.
  /// </summary>
  public partial class DeleteMasterCMSG : ICMSG
  {
    [Export]
    public ulong MasterId { get; set; } = 0;

    /// <summary>
    /// The master name as the player typed it into the confirmation prompt. The server compares it against
    /// the real name and refuses the deletion on a mismatch, so never fill this in from the master data.
    /// </summary>
    [Export]
    public string ConfirmationName { get; set; } = "";

    public override Envelope ToEnvelope()
    {
      var deleteMaster = new global::Bnet.DeleteMasterCMSG
      {
        MasterId = MasterId,
        ConfirmationName = ConfirmationName
      };

      return new Envelope
      {
        DeleteMaster = deleteMaster
      };
    }
  }
}
