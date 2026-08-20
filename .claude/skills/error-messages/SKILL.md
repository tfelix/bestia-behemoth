---
name: error-messages
description: How zone-server tells the client a request was refused (equip denied, party invite failed, master creation rejected, etc). Read this BEFORE adding a new `*ErrorSMSG` class or a new denial/error enum for a handler. Triggers on: ErrorSMSG, OperationError, OpError, denial, Denial, sendDenial, refused, error code, SMSG error.
---

# Error Messages (SMSG)

When a handler needs to tell the client "no" for a specific, structured reason (as opposed to a
generic exception), zone-server sends a small `SMSG` carrying an error code. There are two
patterns in use - **default to the first one**.

## Default: reuse `OperationErrorSMSG` + the shared `OpError` enum

[`OperationErrorSMSG`](../../../zone-server/src/main/kotlin/net/bestia/zone/message/OperationErrorSMSG.kt)
wraps the generic `OperationError` proto message
([`operation_error.proto`](../../../bnet-messages/src/main/proto/messages/system/operation_error.proto)),
which is one `OpError` enum value plus an optional `repeated string args`. It already carries entries for
multiple features (`EQUIP_*`, `MASTER_*`, `TRADE_*`), namespaced by prefix within the single enum.

**When you need a new denial reason for a handler:**

1. Add a value to `OpError` in `operation_error.proto`, prefixed with the feature name (e.g.
   `TRADE_ALREADY_PENDING`), and regenerate protobuf (see [gen-protobuf](../gen-protobuf/SKILL.md)).
2. In the handler, map your domain enum (e.g. a `Denial` sealed enum returned by a service) to the
   matching `OperationErrorProto.OpError` value with a local `when`, and send
   `OperationErrorSMSG(code)`.
3. Add the wording to the client, as one `ERROR_<YOUR_CODE>` row in
   `bestia-client/src/Localization/general.csv` - see below. No client code changes.
4. Do **not** create a new `data class FooErrorSMSG(...)` that just re-wraps `OperationError` with
   its own parallel Kotlin enum - it's pure duplication of `OperationErrorSMSG`, since the proto
   `OpError` enum already carries a distinct, namespaced value per reason.

### The wording: one `general.csv` row, no client code

The wire carries the enum ordinal and nothing else. The client turns it back into a name -
`OperationError.cs` derives `CodeName` via `EnumName.Of`, giving lowercase snake_case
(`chart_needs_blank`) - and `chat.gd` builds the translation key from it: `"ERROR_%s" %
CodeName.to_upper()`. So a new refusal the chat window should voice needs exactly one row in
`general.csv`, keyed `ERROR_` plus the proto value verbatim:

```csv
ERROR_CHART_NEEDS_BLANK,You have no blank vellum to draw on.
```

Codes with no row are ignored on purpose - most belong to a window that words them itself, and
`chat.gd` returns silently rather than showing a raw key. That silence once hid a real bug for the
whole table, so `EnumNameTest` in `bestia-client/tests/BestiaClient.Tests` cross-checks every
`ERROR_*` row against the `OpError` values and fails the build on a typo or a rename. Run
`dotnet test bestia-client/tests/BestiaClient.Tests` after adding a code.

### A message that has to name something: `args`

`OperationError` carries `repeated string args`, and `OperationErrorSMSG` takes them as
`args: List<String>`. They are **substitution values for the client's own template**, in order - a player's
name, a count, a place - never a sentence composed on the server, because the wording and its translation
belong to the client. The client side is the `ERROR_*` row above, which may carry `%s` placeholders;
`OperationError.cs` exposes the values as `Args`, and `chat.gd` substitutes them into the template.

`TRADE_DECLINED` is the worked example: the code says what happened, `args[0]` says who did it, and
`"%s declined the trade."` lives on the client. This is what a denial that has to name somebody uses -
**not** a dedicated `*ErrorSMSG`, and not an English string smuggled through `ChatSMSG`.

This is what [`EquipItemHandler.sendDenial`](../../../zone-server/src/main/kotlin/net/bestia/zone/item/equip/EquipItemHandler.kt)
does: `EquipmentService.Denial` maps to `OpError.EQUIP_*` inline, no dedicated SMSG class.

`MasterErrorSMSG` still wraps its own `MasterErrorCode` enum around `OperationError` the old,
duplicated way - it hasn't been migrated yet (left as-is deliberately when `EquipItemErrorSMSG`
was folded into `OperationErrorSMSG`, to keep that change scoped). Don't copy its shape for new
code; if you're touching it anyway, folding it into `OperationErrorSMSG` too is a welcome
side-cleanup.

## Don't add an `OpError` value the player will never see

Before adding *any* new code, ask whether the denial needs one at all.

A new `OpError` value is for a denial **the player is meant to read and act on** - the name is taken,
every slot is full, the settlement they picked is gone. It is **not** for a state an honest client
cannot produce.

When the client already gates the request - a disabled button, a locally enforced budget, a value
clamped to a range - a rejection arriving server-side means a client bug or a hand-crafted packet.
Still validate it (never trust the client), but answer with the feature's **existing generic code**
(`MASTER_GENERAL_ERROR` and friends) and `LOG.warn` the detail server-side. A dedicated code buys
nothing there: nobody legitimate would ever read the resulting message, and the enum value, the
mapping `when` and the client's error-code branch all have to be maintained forever.

Worked examples, both of which deliberately report `MASTER_GENERAL_ERROR`:

- [`MasterFactory.validateEffortValues`](../../../zone-server/src/main/kotlin/net/bestia/zone/account/master/MasterFactory.kt)
  checks that a new master's effort value distribution spends the creation budget exactly with every
  value in range - but `CreateNewMaster` keeps its Create button disabled until it does.
- [`InvestStatusPointHandler`](../../../zone-server/src/main/kotlin/net/bestia/zone/account/master/status/InvestStatusPointHandler.kt)
  catches `NoStatusPointsAvailableException` - but the status window prices every `+` before enabling
  it.

## Exception: a genuinely distinct payload

[`PartyErrorSMSG`](../../../zone-server/src/main/kotlin/net/bestia/zone/party/PartyErrorSMSG.kt)
has its **own** proto message (`PartyErrorSmsgProto`), not `OperationError`. That's justified only
when the error needs its own wire shape/payload beyond a single code, or is conceptually its own
message family. A plain "this was refused, here's why" enum is not that - it belongs in `OpError`.

## Rule of thumb

> Adding a new error reason a player is meant to read and act on → add an `OpError` value and use
> `OperationErrorSMSG`, with `args` if the message has to name something. Rejecting something an honest
> client can't send → reuse the feature's existing generic code and log it, no new value. Only reach for a
> dedicated `*ErrorSMSG` + proto message when the payload genuinely can't be expressed as a code plus a few
> substitution values.
