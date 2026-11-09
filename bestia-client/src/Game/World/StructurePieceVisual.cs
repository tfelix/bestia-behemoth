using BestiaBehemothClient.Game.World.Mesh;
using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// Draws a building piece as the cells it blocks: a box for each filled cell and a thin panel for each wall.
  /// </summary>
  /// <remarks>
  /// A placeholder until the pieces have models, and an honest one: what is drawn is exactly what the server and
  /// <see cref="WalkRules"/> walk by. Drawn unturned around the bottom centre of the piece's first cell, so turning
  /// the node by the piece's yaw turns it the way <see cref="StructurePieces.Rasterize"/> turns its cells.
  /// </remarks>
  public static class StructurePieceVisual
  {
    /// <summary>How thick a wall is drawn, in voxels. It blocks the whole border either way.</summary>
    private const float WallThickness = 0.15f;

    public static Node3D Build(int kind, Material material, float voxelSize)
    {
      var node = new Node3D();

      foreach (var cell in StructurePieces.CellsOf(kind))
      {
        // In Godot's axes: the server's y is z, and up is y.
        var cellX = (float)cell.X;
        var cellY = (float)cell.Z;
        var cellZ = (float)cell.Y;

        if (cell.Fill > 0)
        {
          var fill = cell.Fill / 255f;
          AddBox(node, material, voxelSize, new Vector3(cellX, cellY + fill / 2f, cellZ), new Vector3(1f, fill, 1f));
        }

        if ((cell.Edges & StructurePieces.EdgeXPlus) != 0)
        {
          AddBox(node, material, voxelSize, new Vector3(cellX + 0.5f, cellY + 0.5f, cellZ), new Vector3(WallThickness, 1f, 1f));
        }

        if ((cell.Edges & StructurePieces.EdgeXMinus) != 0)
        {
          AddBox(node, material, voxelSize, new Vector3(cellX - 0.5f, cellY + 0.5f, cellZ), new Vector3(WallThickness, 1f, 1f));
        }

        if ((cell.Edges & StructurePieces.EdgeYPlus) != 0)
        {
          AddBox(node, material, voxelSize, new Vector3(cellX, cellY + 0.5f, cellZ + 0.5f), new Vector3(1f, 1f, WallThickness));
        }

        if ((cell.Edges & StructurePieces.EdgeYMinus) != 0)
        {
          AddBox(node, material, voxelSize, new Vector3(cellX, cellY + 0.5f, cellZ - 0.5f), new Vector3(1f, 1f, WallThickness));
        }
      }

      return node;
    }

    /// <summary>Where a piece's node goes: the bottom centre of its first cell, in Godot's axes and units.</summary>
    public static Transform3D TransformOf(long originX, long originY, long originZ, float yaw, float voxelSize)
    {
      var turns = StructurePieces.QuarterTurnsOf(yaw);
      var position = new Vector3(originX + 0.5f, originZ, originY + 0.5f) * voxelSize;

      return new Transform3D(new Basis(Vector3.Up, turns * Mathf.Pi / 2f), position);
    }

    /// <param name="centre">In voxels, relative to the piece's node.</param>
    private static void AddBox(Node3D piece, Material material, float voxelSize, Vector3 centre, Vector3 size)
    {
      piece.AddChild(new MeshInstance3D
      {
        Mesh = new BoxMesh { Size = size * voxelSize, Material = material },
        Position = centre * voxelSize
      });
    }
  }
}
