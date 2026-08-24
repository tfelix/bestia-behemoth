using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace BestiaBehemothClient.Auth
{
  /// <summary>
  /// Listens on an ephemeral loopback port for the one redirect the browser makes at the end of the
  /// login, then shuts down.
  /// </summary>
  /// <remarks>
  /// <para>
  /// This is RFC 8252 section 7.3, and it is why the game needs no per-platform code: a plain
  /// <c>http://127.0.0.1:port/</c> navigation is something every browser performs without a prompt,
  /// unlike a custom <c>bestia://</c> scheme, which Safari refuses outright on a redirect and which
  /// Chrome and Firefox gate behind a user gesture.
  /// </para>
  /// <para>
  /// Raw <see cref="TcpListener"/> rather than <c>HttpListener</c>: Microsoft advises against the
  /// latter for new work and its behaviour differs per platform (HTTP.sys on Windows, a managed
  /// implementation elsewhere). One request, one small response, no need for a server.
  /// </para>
  /// <para>
  /// Section 8.3 asks that the port be open only for the duration of the request. It is bound just
  /// before the login starts and closed the moment a callback arrives or the wait is cancelled.
  /// </para>
  /// </remarks>
  public sealed class LoopbackCallbackServer : IDisposable
  {
    /// <summary>
    /// Bound to <see cref="IPAddress.Loopback"/>, so nothing off this machine can reach it however
    /// the host firewall is configured.
    /// </summary>
    private readonly TcpListener _listener;

    private bool _disposed;

    public LoopbackCallbackServer()
    {
      _listener = new TcpListener(IPAddress.Loopback, 0);
      _listener.Start();
      Port = ((IPEndPoint)_listener.LocalEndpoint).Port;
    }

    public int Port { get; }

    /// <summary>
    /// The exact value handed to the login server as <c>redirect_uri</c>. The literal address is
    /// deliberate: the server rejects "localhost", which a hosts file can point off-box.
    /// </summary>
    public string RedirectUri => $"http://127.0.0.1:{Port}/callback";

    /// <summary>
    /// Waits for the browser's redirect and returns the query it carried.
    /// </summary>
    public async Task<CallbackResult> WaitForCallbackAsync(CancellationToken cancellationToken)
    {
      using var registration = cancellationToken.Register(() => _listener.Stop());

      while (true)
      {
        TcpClient client;

        try
        {
          client = await _listener.AcceptTcpClientAsync(cancellationToken).ConfigureAwait(false);
        }
        catch (OperationCanceledException)
        {
          throw;
        }
        catch (ObjectDisposedException)
        {
          cancellationToken.ThrowIfCancellationRequested();
          throw;
        }
        catch (SocketException)
        {
          cancellationToken.ThrowIfCancellationRequested();
          throw;
        }

        using (client)
        {
          var target = await ReadRequestTargetAsync(client, cancellationToken).ConfigureAwait(false);

          if (target == null)
          {
            continue;
          }

          var query = ParseQuery(target);

          // Browsers routinely ask for /favicon.ico alongside a navigation. Answering it and
          // carrying on avoids mistaking it for the callback.
          if (!query.ContainsKey("code") && !query.ContainsKey("error"))
          {
            await RespondAsync(client, "404 Not Found", NotFoundPage, cancellationToken).ConfigureAwait(false);
            continue;
          }

          await RespondAsync(client, "200 OK", SuccessPage, cancellationToken).ConfigureAwait(false);

          query.TryGetValue("code", out var code);
          query.TryGetValue("state", out var state);
          query.TryGetValue("error", out var error);

          return new CallbackResult(code, state, error);
        }
      }
    }

    /// <summary>
    /// Reads only the request line. The body is irrelevant and reading further would just widen the
    /// window in which a local process can hold the listener open.
    /// </summary>
    private static async Task<string> ReadRequestTargetAsync(TcpClient client, CancellationToken cancellationToken)
    {
      var buffer = new byte[MaxRequestLineBytes];
      var read = 0;

      // No ReadTimeout: it does nothing for async reads on NetworkStream. The caller's
      // cancellation token is what bounds this.
      var stream = client.GetStream();

      while (read < buffer.Length)
      {
        var count = await stream
          .ReadAsync(buffer.AsMemory(read, buffer.Length - read), cancellationToken)
          .ConfigureAwait(false);

        if (count == 0)
        {
          break;
        }

        read += count;

        var text = Encoding.ASCII.GetString(buffer, 0, read);
        var lineEnd = text.IndexOf('\n');

        if (lineEnd < 0)
        {
          continue;
        }

        var parts = text.Substring(0, lineEnd).Trim().Split(' ');

        return parts.Length >= 2 ? parts[1] : null;
      }

      return null;
    }

    private static Dictionary<string, string> ParseQuery(string requestTarget)
    {
      var result = new Dictionary<string, string>(StringComparer.Ordinal);
      var separator = requestTarget.IndexOf('?');

      if (separator < 0 || separator == requestTarget.Length - 1)
      {
        return result;
      }

      foreach (var pair in requestTarget.Substring(separator + 1).Split('&'))
      {
        if (pair.Length == 0)
        {
          continue;
        }

        var equals = pair.IndexOf('=');

        if (equals < 0)
        {
          result[Decode(pair)] = string.Empty;
        }
        else
        {
          result[Decode(pair.Substring(0, equals))] = Decode(pair.Substring(equals + 1));
        }
      }

      return result;
    }

    /// <summary>
    /// Form-encoded percent decoding, without pulling in System.Web for three lines. '+' is a
    /// space here because that is what the server's URLEncoder emits, though every value this
    /// callback actually carries is base64url and contains neither.
    /// </summary>
    private static string Decode(string value)
    {
      return Uri.UnescapeDataString(value.Replace("+", "%20"));
    }

    private static async Task RespondAsync(
      TcpClient client,
      string status,
      string body,
      CancellationToken cancellationToken)
    {
      var payload = Encoding.UTF8.GetBytes(body);
      var header = Encoding.ASCII.GetBytes(
        $"HTTP/1.1 {status}\r\n" +
        "Content-Type: text/html; charset=utf-8\r\n" +
        $"Content-Length: {payload.Length}\r\n" +
        "Cache-Control: no-store\r\n" +
        "Connection: close\r\n\r\n");

      var stream = client.GetStream();
      await stream.WriteAsync(header, cancellationToken).ConfigureAwait(false);
      await stream.WriteAsync(payload, cancellationToken).ConfigureAwait(false);
      await stream.FlushAsync(cancellationToken).ConfigureAwait(false);
    }

    public void Dispose()
    {
      if (_disposed)
      {
        return;
      }

      _disposed = true;

      try
      {
        _listener.Stop();
      }
      catch (SocketException)
      {
        // Already torn down by cancellation; nothing left to release.
      }
    }

    public sealed record CallbackResult(string Code, string State, string Error);

    private const int MaxRequestLineBytes = 8192;

    private const string SuccessPage =
      "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">" +
      "<title>Bestia</title></head><body style=\"font-family:system-ui;text-align:center;padding:4rem\">" +
      "<h1>You are signed in</h1><p>You can close this tab and return to the game.</p></body></html>";

    private const string NotFoundPage =
      "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">" +
      "<title>Bestia</title></head><body></body></html>";
  }
}
