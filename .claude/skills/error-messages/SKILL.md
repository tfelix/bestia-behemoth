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
which is just one `OpError` enum value. It already carries entries for multiple features
(`EQUIP_*`, `MASTER_*`), namespaced by prefix within the single enum.

**When you need a new denial reason for a handler:**

1. Add a value to `OpError` in `operation_error.proto`, prefixed with the feature name (e.g.
   `TRADE_ALREADY_PENDING`), and regenerate protobuf (see [gen-protobuf](../gen-protobuf/SKILL.md)).
2. In the handler, map your domain enum (e.g. a `Denial` sealed enum returned by a service) to the
   matching `OperationErrorProto.OpError` value with a local `when`, and send
   `OperationErrorSMSG(code)`.
3. Do **not** create a new `data class FooErrorSMSG(...)` that just re-wraps `OperationError` with
   its own parallel Kotlin enum - it's pure duplication of `OperationErrorSMSG`, since the proto
   `OpError` enum already carries a distinct, namespaced value per reason.

This is what [`EquipItemHandler.sendDenial`](../../../zone-server/src/main/kotlin/net/bestia/zone/item/equip/EquipItemHandler.kt)
does: `EquipmentService.Denial` maps to `OpError.EQUIP_*` inline, no dedicated SMSG class.

`MasterErrorSMSG` still wraps its own `MasterErrorCode` enum around `OperationError` the old,
duplicated way - it hasn't been migrated yet (left as-is deliberately when `EquipItemErrorSMSG`
was folded into `OperationErrorSMSG`, to keep that change scoped). Don't copy its shape for new
code; if you're touching it anyway, folding it into `OperationErrorSMSG` too is a welcome
side-cleanup.

## Exception: a genuinely distinct payload

[`PartyErrorSMSG`](../../../zone-server/src/main/kotlin/net/bestia/zone/party/PartyErrorSMSG.kt)
has its **own** proto message (`PartyErrorSmsgProto`), not `OperationError`. That's justified only
when the error needs its own wire shape/payload beyond a single code, or is conceptually its own
message family. A plain "this was refused, here's why" enum is not that - it belongs in `OpError`.

## Rule of thumb

> Adding a new error reason to an existing feature (or a closely related one) → add an `OpError`
> value and use `OperationErrorSMSG`. Only reach for a dedicated `*ErrorSMSG` + proto message when
> the payload genuinely can't be expressed as a single code.
