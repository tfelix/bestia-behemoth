using System.Collections.Generic;
using System.Globalization;
using Godot;

namespace BestiaBehemothClient.Bnet.Message
{
  /// <summary>
  /// The last few minutes of <see cref="NetLog"/> lines, for bug reports.
  /// </summary>
  /// <remarks>
  /// A <see cref="RefCounted"/> only so GDScript can create one and read the shared buffer through it. Lines arrive
  /// from the socket thread, hence the lock.
  /// </remarks>
  public partial class NetLogTail : RefCounted
  {
    private const int Capacity = 240;
    private static readonly Queue<string> Lines = new();
    private static readonly object Guard = new();

    public static void Remember(string line)
    {
      lock (Guard)
      {
        var seconds = (Time.GetTicksMsec() / 1000.0).ToString("F1", CultureInfo.InvariantCulture);
        Lines.Enqueue($"{seconds}s {line}");
        while (Lines.Count > Capacity)
        {
          Lines.Dequeue();
        }
      }
    }

    public string Text()
    {
      lock (Guard)
      {
        return string.Join("\n", Lines);
      }
    }
  }
}
