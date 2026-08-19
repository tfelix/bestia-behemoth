using System.Linq;
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
    /// <see cref="Code"/> as its lowercase enum name, for the GDScript side. GDScript cannot see a C#
    /// enum's members, so matching on this is what keeps a new denial reason from being re-declared as a
    /// bare ordinal over there - the duplication <c>DialogArg.KindName</c> was introduced to stop.
    /// </summary>
    [Export]
    public string CodeName { get; set; } = string.Empty;

    /// <summary>
    /// Substitution values for the message template the client holds for <see cref="CodeName"/>, in order -
    /// a player's name, a count, a place. The server sends values and never a finished sentence, so the
    /// wording and its translation stay here. Empty for most codes.
    /// </summary>
    [Export]
    public string[] Args { get; set; } = [];

    /// <summary>
    /// Creates an OperationError message from protobuf data
    /// </summary>
    /// <param name="protoOperationError">The protobuf OperationError object</param>
    /// <returns>Godot-friendly OperationError object</returns>
    public static OperationError FromProto(global::Bnet.OperationError protoOperationError)
    {
      return new OperationError
      {
        Code = (int)protoOperationError.Code,
        CodeName = protoOperationError.Code.ToString().ToLowerInvariant(),
        Args = protoOperationError.Args.ToArray()
      };
    }

    /// <summary>
    /// Converts this OperationError back to protobuf format
    /// </summary>
    /// <returns>Protobuf OperationError object</returns>
    public global::Bnet.OperationError ToProto()
    {
      var proto = new global::Bnet.OperationError
      {
        Code = (global::Bnet.OpError)Code
      };
      proto.Args.AddRange(Args);

      return proto;
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