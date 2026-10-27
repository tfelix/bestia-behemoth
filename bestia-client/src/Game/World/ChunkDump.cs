using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using Godot;

namespace BestiaBehemothClient.Game.World
{
  /// <summary>
  /// A snapshot of the streamed chunks around the camera, and the camera itself, written to a file.
  /// </summary>
  /// <remarks>
  /// For the dev harnesses. A rendering fault that only the real world's geometry shows - the coast's waterline
  /// was one - cannot be iterated on against a hand-built test scene, and the real world needs two servers and a
  /// login. A dump taken once in game lets a harness mesh that exact ground with the real renderer and look at it
  /// from the exact view the screenshot was taken from.
  ///
  /// <para>
  /// The voxels exactly as the store holds them, block ids and occupancy, so meshing a dump is meshing the world.
  /// Gzipped: a chunk is half a megabyte raw, most of it air and solid runs.
  /// </para>
  /// </remarks>
  public static class ChunkDump
  {
    private const uint Magic = 0x504D4443; // "CDMP"
    private const int Version = 1;

    /// <summary>A dump read back: the chunks, and the camera that was looking at them.</summary>
    public sealed class Snapshot
    {
      public List<(ChunkKey Key, VoxelChunk Chunk)> Chunks { get; } = new();
      public Transform3D Camera { get; set; }
      public float Fov { get; set; }
    }

    public static void Write(string path, Mesh.IChunkSource store, IEnumerable<ChunkKey> keys, Camera3D camera)
    {
      var absolute = ProjectSettings.GlobalizePath(path);
      Directory.CreateDirectory(Path.GetDirectoryName(absolute)!);

      using var file = File.Create(absolute);
      using var gzip = new GZipStream(file, CompressionLevel.Fastest);
      using var writer = new BinaryWriter(gzip);

      writer.Write(Magic);
      writer.Write(Version);

      var transform = camera.GlobalTransform;
      WriteVector(writer, transform.Basis.X);
      WriteVector(writer, transform.Basis.Y);
      WriteVector(writer, transform.Basis.Z);
      WriteVector(writer, transform.Origin);
      writer.Write(camera.Fov);

      var chunks = new List<(ChunkKey, VoxelChunk)>();
      foreach (var key in keys)
      {
        var chunk = store.Get(key);
        if (chunk != null)
        {
          chunks.Add((key, chunk));
        }
      }

      writer.Write(chunks.Count);
      foreach (var (key, chunk) in chunks)
      {
        writer.Write(key.X);
        writer.Write(key.Y);
        writer.Write(key.Z);
        writer.Write(chunk.Size);
        writer.Write(chunk.Height);
        writer.Write(chunk.Blocks);
        writer.Write(chunk.Occupancy);
      }
    }

    public static Snapshot Read(string path)
    {
      using var file = File.OpenRead(ProjectSettings.GlobalizePath(path));
      using var gzip = new GZipStream(file, CompressionMode.Decompress);
      using var reader = new BinaryReader(gzip);

      if (reader.ReadUInt32() != Magic)
      {
        throw new InvalidDataException($"{path} is not a chunk dump");
      }

      var version = reader.ReadInt32();
      if (version != Version)
      {
        throw new InvalidDataException($"{path} is chunk dump version {version}, this build reads {Version}");
      }

      var snapshot = new Snapshot();
      var basis = new Basis(ReadVector(reader), ReadVector(reader), ReadVector(reader));
      snapshot.Camera = new Transform3D(basis, ReadVector(reader));
      snapshot.Fov = reader.ReadSingle();

      var count = reader.ReadInt32();
      for (var i = 0; i < count; i++)
      {
        var key = new ChunkKey(reader.ReadInt32(), reader.ReadInt32(), reader.ReadInt32());
        var size = reader.ReadInt32();
        var height = reader.ReadInt32();
        var volume = size * size * height;
        var blocks = reader.ReadBytes(volume);
        var occupancy = reader.ReadBytes(volume);
        snapshot.Chunks.Add((key, new VoxelChunk(key.X, key.Y, key.Z, size, height, blocks, occupancy)));
      }

      return snapshot;
    }

    private static void WriteVector(BinaryWriter writer, Vector3 vector)
    {
      writer.Write(vector.X);
      writer.Write(vector.Y);
      writer.Write(vector.Z);
    }

    private static Vector3 ReadVector(BinaryReader reader) =>
      new(reader.ReadSingle(), reader.ReadSingle(), reader.ReadSingle());
  }
}
