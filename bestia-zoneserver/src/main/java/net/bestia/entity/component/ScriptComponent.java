package net.bestia.entity.component;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Holds various script callbacks for entities.
 *
 * @author Thomas Felix
 */
public class ScriptComponent extends Component {

  /**
   * Callback types of the different scripts. This is used to register
   * different callbacks for different hooks in the bestia engine. For further
   * information which variables are available for the different script
   * environments consult the various script environment implementations in
   * the bestia-zone module.
   */
  public enum TriggerType {

    /**
     * Script is called on a regular time basis.
     */
    ON_INTERVAL,

    /**
     * Script gets called for every entity entering the area.
     */
    ON_ENTER_AREA,

    /**
     * Script gets called for every entity leaving the area.
     */
    ON_LEAVE_AREA,

    /**
     * Script is called if the entity is damage awarded.
     */
    ON_TAKE_DMG,

    /**
     * This hook gets called before the damage is calculated to the script
     * so the script can influence the damage calculation.
     */
    ON_BEFORE_TAKE_DMG,

    /**
     * The script is called if a attack is about to be processed.
     */
    ON_ATTACK,

    /**
     * Script is called if an item is picked up by a player.
     */
    ON_ITEM_PICKUP,

    /**
     * Script is called if a player drops an item.
     */
    ON_ITEM_DROP
  }

  public static class ScriptCallback implements Serializable {
    private final String uuid;
    private final int intervalMs;
    private final TriggerType type;
    private final String script;

    public ScriptCallback(String uuid, TriggerType type, String script, int intervalMs) {
      this.uuid = uuid;
      this.type = type;
      this.script = script;
      this.intervalMs = intervalMs;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ScriptCallback that = (ScriptCallback) o;
      return java.util.Objects.equals(uuid, that.uuid) &&
              type == that.type &&
              java.util.Objects.equals(script, that.script);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(uuid, type, script);
    }

    public String getScript() {
      return script;
    }

    public int getIntervalMs() {
      return intervalMs;
    }

    public String getUuid() {
      return uuid;
    }
  }

  private static final long serialVersionUID = 1L;

  private final Map<String, ScriptCallback> callbacks = new HashMap<>();

  public ScriptComponent(long id) {
    super(id, 0);
    // no op.
  }

  public ScriptCallback getCallback(String uuid) {
    return callbacks.get(uuid);
  }

  public void addCallback(ScriptCallback callback) {
    callbacks.put(callback.uuid, callback);
  }

  public void removeCallback(String uuid) {
    callbacks.remove(uuid);
  }

  public Set<String> getAllScriptUids() {
    return callbacks.keySet();
  }

  @Override
  public void clear() {
    callbacks.clear();
  }

  @Override
  public String toString() {
    return String.format("ScriptComponent[id: %d]", getId());
  }
}