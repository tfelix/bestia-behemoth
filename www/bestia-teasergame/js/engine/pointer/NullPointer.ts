import { Pointer } from "./Pointer";
import { PointerManager } from "./PointerManager";
import { EngineContext } from "../EngineContext";

export class NullPointer extends Pointer {
  constructor(
    manager: PointerManager,
    ctx: EngineContext
  ) {
    super(manager, ctx);
  }

  allowOverwrite(): boolean {
    return true;
  }
}
