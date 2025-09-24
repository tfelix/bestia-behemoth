using Godot;

namespace BestiaBehemothClient.Bnet.Message
{
  /// <summary>
  /// Godot-friendly wrapper for OperationError protobuf data
  /// </summary>
  [GlobalClass]
  public partial class OperationError : ISMSG
  {
    [Export]
    public int Code { get; set; }

    /// <summary>
    /// Creates an OperationError message from protobuf data
    /// </summary>
    /// <param name="protoOperationError">The protobuf OperationError object</param>
    /// <returns>Godot-friendly OperationError object</returns>
    public static OperationError FromProto(global::Bnet.OperationError protoOperationError)
    {
      return new OperationError
      {
        Code = (int)protoOperationError.Code
      };
    }

    /// <summary>
    /// Converts this OperationError back to protobuf format
    /// </summary>
    /// <returns>Protobuf OperationError object</returns>
    public global::Bnet.OperationError ToProto()
    {
      return new global::Bnet.OperationError
      {
        Code = (global::Bnet.OpError)Code
      };
    }

    /// <summary>
    /// Gets the error code as the original enum type for C# usage
    /// </summary>
    public global::Bnet.OpError ErrorCode => (global::Bnet.OpError)Code;

    /// <summary>
    /// Checks if the error is related to master name already being taken
    /// </summary>
    public bool IsMasterNameAlreadyTaken => ErrorCode == global::Bnet.OpError.MasterNameAlreadyTaken;

    /// <summary>
    /// Checks if the error is related to maximum masters limit reached
    /// </summary>
    public bool IsMasterMaxReached => ErrorCode == global::Bnet.OpError.MasterMaxMastersReached;

    /// <summary>
    /// Checks if the error is related to invalid master name
    /// </summary>
    public bool IsMasterInvalidName => ErrorCode == global::Bnet.OpError.MasterInvalidName;

    /// <summary>
    /// Checks if the error is a general master error
    /// </summary>
    public bool IsMasterGeneralError => ErrorCode == global::Bnet.OpError.MasterGeneralError;
  }
}