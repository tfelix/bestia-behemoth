using System.Collections.Generic;

namespace BestiaBehemothClient.Bnet.Message
{
  /// <summary>
  /// A path of tile steps on the wire: the first waypoint, then each following one as its difference to the last.
  /// Server axes throughout; see <see cref="Vec3Convert"/> for the swap to Godot's.
  /// </summary>
  public static class DeltaPathConvert
  {
    private const int Axes = 3;

    public static global::Bnet.DeltaPath Encode(IReadOnlyList<global::Bnet.Vec3> path)
    {
      var encoded = new global::Bnet.DeltaPath();
      if (path.Count == 0)
      {
        return encoded;
      }

      encoded.First = path[0];
      for (int i = 1; i < path.Count; i++)
      {
        encoded.Deltas.Add(checked((int)(path[i].X - path[i - 1].X)));
        encoded.Deltas.Add(checked((int)(path[i].Y - path[i - 1].Y)));
        encoded.Deltas.Add(checked((int)(path[i].Z - path[i - 1].Z)));
      }

      return encoded;
    }

    /// <summary>An incomplete trailing step is dropped.</summary>
    public static List<global::Bnet.Vec3> Decode(global::Bnet.DeltaPath encoded)
    {
      var path = new List<global::Bnet.Vec3>();
      if (encoded?.First == null)
      {
        return path;
      }

      var current = encoded.First;
      path.Add(current);

      for (int i = 0; i + Axes <= encoded.Deltas.Count; i += Axes)
      {
        current = new global::Bnet.Vec3
        {
          X = current.X + encoded.Deltas[i],
          Y = current.Y + encoded.Deltas[i + 1],
          Z = current.Z + encoded.Deltas[i + 2]
        };
        path.Add(current);
      }

      return path;
    }
  }
}
