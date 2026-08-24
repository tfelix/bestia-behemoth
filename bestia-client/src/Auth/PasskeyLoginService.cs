using System;
using System.Threading;
using System.Threading.Tasks;
using Godot;

namespace BestiaBehemothClient.Auth
{
  /// <summary>
  /// Drives passkey login from the game's side.
  /// </summary>
  /// <remarks>
  /// <para>
  /// The whole of the game's involvement is: bind a loopback port, ask the login server to open a
  /// session, hand the URL to the system browser, wait for the redirect, and trade the code for a
  /// token. WebAuthn itself - Windows Hello, Touch ID, iCloud Keychain, a security key, a phone
  /// over QR - happens inside the browser, which is what keeps this file free of platform code.
  /// </para>
  /// <para>
  /// The token this produces is the same zone JWT the static development login returns, so the
  /// connection sequence after it is identical.
  /// </para>
  /// </remarks>
  [GlobalClass]
  public partial class PasskeyLoginService : Node
  {
    /// <summary>Carries the zone JWT, ready to be sent in the socket handshake.</summary>
    [Signal]
    public delegate void LoginSucceededEventHandler(string token);

    [Signal]
    public delegate void LoginFailedEventHandler(string reason);

    /// <summary>The browser has been opened and the callback is being waited for.</summary>
    [Signal]
    public delegate void AwaitingBrowserEventHandler();

    /// <summary>
    /// Matches `game-login.session-ttl-seconds` on the server. Waiting longer than the session can
    /// live only leaves a listener open for a code that can no longer be minted.
    /// </summary>
    [Export]
    public int TimeoutSeconds { get; set; } = 300;

    private CancellationTokenSource _cancellation;

    /// <summary>True while a login is in flight, so the UI can refuse to start a second one.</summary>
    public bool IsBusy => _cancellation != null;

    public void StartLogin(string loginServerUrl)
    {
      Start(loginServerUrl, register: false);
    }

    public void StartRegistration(string loginServerUrl)
    {
      Start(loginServerUrl, register: true);
    }

    public void Cancel()
    {
      _cancellation?.Cancel();
    }

    private void Start(string loginServerUrl, bool register)
    {
      if (IsBusy)
      {
        GD.PushWarning("PasskeyLoginService: a login is already in progress");
        return;
      }

      _cancellation = new CancellationTokenSource(TimeSpan.FromSeconds(TimeoutSeconds));

      // Fire and forget on purpose: the result comes back as a signal on the main thread, which is
      // the only way a Node may talk to the rest of the scene tree.
      _ = RunAsync(loginServerUrl, register, _cancellation.Token);
    }

    private async Task RunAsync(string loginServerUrl, bool register, CancellationToken cancellationToken)
    {
      try
      {
        // Bind before starting the session: the server records the redirect target, so the port has
        // to be known first.
        using var callbackServer = new LoopbackCallbackServer();
        using var api = new AuthApiClient(loginServerUrl);

        var verifier = Pkce.CreateVerifier();
        var state = Pkce.CreateState();

        var session = await api
          .StartAsync(callbackServer.RedirectUri, Pkce.Challenge(verifier), state, register, cancellationToken)
          .ConfigureAwait(false);

        if (string.IsNullOrEmpty(session?.LoginUrl))
        {
          Fail("The login server did not return a sign-in link.");
          return;
        }

        OpenBrowser(session.LoginUrl);
        Emit(SignalName.AwaitingBrowser);

        var callback = await callbackServer
          .WaitForCallbackAsync(cancellationToken)
          .ConfigureAwait(false);

        if (!string.IsNullOrEmpty(callback.Error))
        {
          Fail("Sign-in was refused in the browser.");
          return;
        }

        // The state check is what tells our own redirect apart from one another local process
        // aimed at this port. Compared before the code is spent, so a forged callback cannot burn
        // the real one.
        if (callback.State != state)
        {
          Fail("The sign-in response did not match this request.");
          return;
        }

        if (string.IsNullOrEmpty(callback.Code))
        {
          Fail("The sign-in response carried no code.");
          return;
        }

        var token = await api
          .ExchangeAsync(callback.Code, verifier, cancellationToken)
          .ConfigureAwait(false);

        if (string.IsNullOrEmpty(token))
        {
          Fail("The login server did not return a token.");
          return;
        }

        Emit(SignalName.LoginSucceeded, token);
      }
      catch (OperationCanceledException)
      {
        Fail("Sign-in was cancelled.");
      }
      catch (Exception e)
      {
        // The detail goes to the log; the player is told something they can act on.
        GD.PushError($"PasskeyLoginService: login failed - {e}");
        Fail("Could not reach the login server.");
      }
      finally
      {
        var cancellation = _cancellation;
        _cancellation = null;
        cancellation?.Dispose();
      }
    }

    /// <summary>
    /// The entire platform-specific surface of passkey login: Godot maps this onto ShellExecute,
    /// xdg-open and NSWorkspace respectively.
    /// </summary>
    private static void OpenBrowser(string url)
    {
      var error = OS.ShellOpen(url);

      if (error != Error.Ok)
      {
        throw new InvalidOperationException($"OS.ShellOpen returned {error}");
      }
    }

    private void Fail(string reason)
    {
      Emit(SignalName.LoginFailed, reason);
    }

    /// <summary>
    /// Signals must be raised on the main thread; everything above this runs on a task thread.
    /// </summary>
    private void Emit(StringName signal, string argument = null)
    {
      if (argument == null)
      {
        Callable.From(() => EmitSignal(signal)).CallDeferred();
      }
      else
      {
        Callable.From(() => EmitSignal(signal, argument)).CallDeferred();
      }
    }

    public override void _ExitTree()
    {
      Cancel();
    }
  }
}
