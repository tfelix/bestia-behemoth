using System;
using System.Collections.Generic;

namespace BestiaBehemothClient.Game.World.Mesh
{
  /// <summary>
  /// The cells each building piece fills, and how a placed piece turns into cells: a copy of the server's
  /// <c>structure-pieces.yml</c> and <c>StructureRasterizer</c>, so a step is predicted the way the server walks it.
  /// </summary>
  /// <remarks>
  /// Coordinates are the server's: x and y across, z up. <c>StructurePiecesTest</c> checks this copy against the
  /// server's golden file, <c>structure-cells.golden.json</c>.
  /// </remarks>
  public static class StructurePieces
  {
    public const int EdgeXPlus = 1;
    public const int EdgeYPlus = 2;
    public const int EdgeXMinus = 4;
    public const int EdgeYMinus = 8;
    public const int AllEdges = EdgeXPlus | EdgeYPlus | EdgeXMinus | EdgeYMinus;

    /// <summary><c>StaticEntityKind</c> ordinals of the pieces.</summary>
    public const int Wall = 52;
    public const int Floor = 53;
    public const int Stairs = 54;

    private const int Full = 255;
    private const int Half = 128;

    /// <summary>A cell of a piece before it is placed, or of a placed piece in the world.</summary>
    /// <param name="Fill">How full the cell is from the bottom up, 0 to 255.</param>
    /// <param name="Edges">The walls on its borders, as <c>Edge*</c> bits.</param>
    public readonly record struct Cell(long X, long Y, long Z, int Fill, int Edges);

    private static readonly Dictionary<int, Cell[]> Pieces = new()
    {
      [Wall] = new[]
      {
        new Cell(0, 0, 0, 0, EdgeXPlus), new Cell(0, 0, 1, 0, EdgeXPlus), new Cell(0, 0, 2, 0, EdgeXPlus),
        new Cell(0, 1, 0, 0, EdgeXPlus), new Cell(0, 1, 1, 0, EdgeXPlus), new Cell(0, 1, 2, 0, EdgeXPlus)
      },
      [Floor] = new[]
      {
        new Cell(0, 0, 0, Half, 0), new Cell(1, 0, 0, Half, 0), new Cell(0, 1, 0, Half, 0), new Cell(1, 1, 0, Half, 0)
      },
      [Stairs] = new[]
      {
        new Cell(0, 0, 0, Half, 0),
        new Cell(1, 0, 0, Full, 0),
        new Cell(2, 0, 0, Full, 0), new Cell(2, 0, 1, Half, 0),
        new Cell(3, 0, 0, Full, 0), new Cell(3, 0, 1, Full, 0),
        new Cell(4, 0, 0, Full, 0), new Cell(4, 0, 1, Full, 0), new Cell(4, 0, 2, Half, 0),
        new Cell(5, 0, 0, Full, 0), new Cell(5, 0, 1, Full, 0), new Cell(5, 0, 2, Full, 0)
      }
    };

    public static bool IsPiece(int kind) => Pieces.ContainsKey(kind);

    /// <summary>A piece's cells before it is placed or turned, or none for a kind that is not a piece.</summary>
    public static IReadOnlyList<Cell> CellsOf(int kind) => Pieces.TryGetValue(kind, out var cells) ? cells : Array.Empty<Cell>();

    /// <summary>
    /// Whether a piece fills nothing in its lowest layer, like a wall: it then stands in the cell of a floor rather
    /// than on top of it.
    /// </summary>
    public static bool StandsInFloor(int kind)
    {
      var stands = false;
      foreach (var cell in CellsOf(kind))
      {
        if (cell.Z != 0)
        {
          continue;
        }

        if (cell.Fill > 0)
        {
          return false;
        }

        stands = true;
      }

      return stands;
    }

    /// <summary>The nearest quarter turn to a yaw in radians, halves rounded up as the server's <c>roundToInt</c>.</summary>
    public static int QuarterTurnsOf(float yaw) => FloorMod((int)Math.Floor(yaw / (Math.PI / 2) + 0.5), 4);

    /// <summary>
    /// The cells of a piece placed at an origin and turned the way a model is turned by the same yaw: one turn takes
    /// x+ to y-. A wall is also written into the cell on the other side of its border.
    /// </summary>
    public static List<Cell> Rasterize(int kind, long originX, long originY, long originZ, int quarterTurns)
    {
      var cells = new List<Cell>();
      if (!Pieces.TryGetValue(kind, out var piece))
      {
        return cells;
      }

      var turns = FloorMod(quarterTurns, 4);
      foreach (var local in piece)
      {
        var (dx, dy) = Turned(local.X, local.Y, turns);
        var x = originX + dx;
        var y = originY + dy;
        var z = originZ + local.Z;
        var edges = RotatedEdges(local.Edges, -turns);

        cells.Add(new Cell(x, y, z, local.Fill, edges));
        foreach (var side in new[] { EdgeXPlus, EdgeYPlus, EdgeXMinus, EdgeYMinus })
        {
          if ((edges & side) == 0)
          {
            continue;
          }

          var (stepX, stepY) = StepThrough(side);
          cells.Add(new Cell(x + stepX, y + stepY, z, 0, OppositeEdges(side)));
        }
      }

      return cells;
    }

    /// <summary>The sides a step of (dx, dy) leaves its column through: two of them for a diagonal.</summary>
    public static int EdgesCrossedBy(long dx, long dy)
    {
      var edges = 0;
      if (dx > 0) edges |= EdgeXPlus;
      if (dx < 0) edges |= EdgeXMinus;
      if (dy > 0) edges |= EdgeYPlus;
      if (dy < 0) edges |= EdgeYMinus;

      return edges;
    }

    /// <summary>The same sides seen from the column on the other side of them.</summary>
    public static int OppositeEdges(int edges) => RotatedEdges(edges, 2);

    /// <summary>Edge bits turned counter-clockwise: x+ becomes y+.</summary>
    private static int RotatedEdges(int edges, int quarterTurns)
    {
      var turns = FloorMod(quarterTurns, 4);
      return ((edges << turns) | (edges >> (4 - turns))) & AllEdges;
    }

    private static (long, long) Turned(long x, long y, int turns) => turns switch
    {
      0 => (x, y),
      1 => (y, -x),
      2 => (-x, -y),
      _ => (-y, x)
    };

    private static (long, long) StepThrough(int side) => side switch
    {
      EdgeXPlus => (1, 0),
      EdgeYPlus => (0, 1),
      EdgeXMinus => (-1, 0),
      _ => (0, -1)
    };

    private static int FloorMod(int value, int divisor) => ((value % divisor) + divisor) % divisor;
  }
}
