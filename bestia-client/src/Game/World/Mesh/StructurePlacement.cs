using System;

namespace BestiaBehemothClient.Game.World.Mesh
{
  /// <summary>
  /// Where a building piece snaps to under the pointer, and whether the server would take it there.
  /// </summary>
  /// <remarks>
  /// A ray from the camera is walked voxel by voxel. The first built cell, wall or terrain it meets decides the spot:
  /// the free cell in front of what was hit, or the cell on top of a wall when the ray hit the wall's top metre. So a
  /// piece snaps onto the ground and onto other pieces on the same 1 m grid the server keeps.
  /// </remarks>
  public static class StructurePlacement
  {
    /// <param name="Valid">
    /// Whether the cells are free and the piece touches the ground or another piece. The server also asks how stable
    /// it would stand, which this does not, so a piece reaching too far out still comes back refused.
    /// </param>
    public readonly record struct Spot(long X, long Y, long Z, bool Valid);

    /// <summary>
    /// The spot for <paramref name="kind"/> under a ray, in server coordinates, or null when the ray hits nothing held
    /// within <paramref name="maxDistance"/> voxels.
    /// </summary>
    public static Spot? Solve(
      IChunkSource source, ChunkWrap wrap, int chunkSize, int chunkHeight, StructureCells cells, int kind,
      double fromX, double fromY, double fromZ, double dirX, double dirY, double dirZ, int quarterTurns,
      double maxDistance)
    {
      var hit = FirstFreeCellOnRay(source, wrap, chunkSize, chunkHeight, cells, fromX, fromY, fromZ, dirX, dirY, dirZ, maxDistance);
      if (hit == null)
      {
        return null;
      }

      var (x, y, z) = hit.Value;

      // A wall stands in the cell of a floor, not on top of it, so walls line up with those on the ground beside it.
      var fillBelow = cells.FillAt(x, y, z - 1);
      if (StructurePieces.StandsInFloor(kind) && fillBelow > 0 && fillBelow < 255)
      {
        z--;
      }

      var placed = StructurePieces.Rasterize(kind, x, y, z, quarterTurns);
      return new Spot(x, y, z, IsFree(source, wrap, chunkSize, chunkHeight, cells, placed) && IsHeld(source, wrap, chunkSize, chunkHeight, cells, placed));
    }

    private static (long, long, long)? FirstFreeCellOnRay(
      IChunkSource source, ChunkWrap wrap, int chunkSize, int chunkHeight, StructureCells cells,
      double fromX, double fromY, double fromZ, double dirX, double dirY, double dirZ, double maxDistance)
    {
      long x = (long)Math.Floor(fromX), y = (long)Math.Floor(fromY), z = (long)Math.Floor(fromZ);
      int stepX = Math.Sign(dirX), stepY = Math.Sign(dirY), stepZ = Math.Sign(dirZ);
      double nextX = Boundary(fromX, dirX, x), nextY = Boundary(fromY, dirY, y), nextZ = Boundary(fromZ, dirZ, z);
      double deltaX = Delta(dirX), deltaY = Delta(dirY), deltaZ = Delta(dirZ);

      while (Math.Min(nextX, Math.Min(nextY, nextZ)) <= maxDistance)
      {
        long fromCellX = x, fromCellY = y, fromCellZ = z;
        var crossed = 0;

        if (nextX <= nextY && nextX <= nextZ)
        {
          x += stepX;
          nextX += deltaX;
          crossed = StructurePieces.EdgesCrossedBy(stepX, 0);
        }
        else if (nextY <= nextZ)
        {
          y += stepY;
          nextY += deltaY;
          crossed = StructurePieces.EdgesCrossedBy(0, stepY);
        }
        else
        {
          z += stepZ;
          nextZ += deltaZ;
        }

        if ((cells.EdgesAt(fromCellX, fromCellY, fromCellZ) & crossed) != 0)
        {
          var top = fromCellZ;
          while ((cells.EdgesAt(fromCellX, fromCellY, top + 1) & crossed) != 0)
          {
            top++;
          }

          return (fromCellX, fromCellY, fromCellZ == top ? top + 1 : fromCellZ);
        }

        var block = WalkRules.BlockAt(source, wrap, chunkSize, chunkHeight, x, y, (int)z, out _);
        if (block < 0)
        {
          return null;
        }

        if (cells.FillAt(x, y, z) > 0 || WalkRules.IsSolid(block))
        {
          return (fromCellX, fromCellY, fromCellZ);
        }
      }

      return null;
    }

    private static bool IsFree(
      IChunkSource source, ChunkWrap wrap, int chunkSize, int chunkHeight, StructureCells cells,
      System.Collections.Generic.List<StructurePieces.Cell> placed)
    {
      foreach (var cell in placed)
      {
        if ((cells.EdgesAt(cell.X, cell.Y, cell.Z) & cell.Edges) != 0)
        {
          return false;
        }

        if (cell.Fill == 0)
        {
          continue;
        }

        var block = WalkRules.BlockAt(source, wrap, chunkSize, chunkHeight, cell.X, cell.Y, (int)cell.Z, out _);
        if (cells.FillAt(cell.X, cell.Y, cell.Z) > 0 || block < 0 || WalkRules.IsSolid(block))
        {
          return false;
        }
      }

      return true;
    }

    /// <summary>On terrain, or touching a piece that stands: what the server's stability starts from.</summary>
    private static bool IsHeld(
      IChunkSource source, ChunkWrap wrap, int chunkSize, int chunkHeight, StructureCells cells,
      System.Collections.Generic.List<StructurePieces.Cell> placed)
    {
      foreach (var cell in placed)
      {
        var below = WalkRules.BlockAt(source, wrap, chunkSize, chunkHeight, cell.X, cell.Y, (int)cell.Z - 1, out _);
        if (below >= 0 && WalkRules.IsSolid(below))
        {
          return true;
        }

        if (cells.IsBuilt(cell.X, cell.Y, cell.Z) || cells.IsBuilt(cell.X, cell.Y, cell.Z - 1) ||
            cells.IsBuilt(cell.X, cell.Y, cell.Z + 1) ||
            cells.IsBuilt(cell.X + 1, cell.Y, cell.Z) || cells.IsBuilt(cell.X - 1, cell.Y, cell.Z) ||
            cells.IsBuilt(cell.X, cell.Y + 1, cell.Z) || cells.IsBuilt(cell.X, cell.Y - 1, cell.Z))
        {
          return true;
        }
      }

      return false;
    }

    /// <summary>Ray distance to the first border along one axis.</summary>
    private static double Boundary(double from, double dir, long cell)
    {
      if (dir > 0)
      {
        return (cell + 1 - from) / dir;
      }

      return dir < 0 ? (cell - from) / dir : double.PositiveInfinity;
    }

    private static double Delta(double dir) => dir == 0 ? double.PositiveInfinity : Math.Abs(1.0 / dir);
  }
}
