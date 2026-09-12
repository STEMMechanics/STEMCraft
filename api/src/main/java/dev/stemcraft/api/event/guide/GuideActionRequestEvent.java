package dev.stemcraft.api.event.guide;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Server-thread request for an optional interactive guide step. Providers register an
 * ordinary event listener, match a namespaced action key and claim the request. The caller
 * cancels it when its guide closes, times out or changes topic. No provider needs to depend
 * on the guide implementation. All methods and completion callbacks run on the server thread.
 */
public final class GuideActionRequestEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String action;
    private final Consumer<Boolean> completion;
    private Runnable cleanup;
    private boolean claimed;
    private boolean finished;

    /**
     * @param player owner of the private step
     * @param action namespaced callback key, such as stemcraft:quest-practice
     * @param completion receives true only after verified success, false on provider failure
     */
    public GuideActionRequestEvent(Player player, String action, Consumer<Boolean> completion) {
        this.player = Objects.requireNonNull(player);
        this.action = Objects.requireNonNull(action);
        this.completion = Objects.requireNonNull(completion);
    }
    /** @return player who requested the step */
    public Player player() { return player; }
    /** @return exact registered action key */
    public String action() { return action; }
    /** @return whether any provider accepted the request */
    public boolean claimed() { return claimed; }
    /** @return whether completion or cancellation already ended the request */
    public boolean finished() { return finished; }

    /**
     * Claim once before creating resources. Cleanup runs exactly once on completion/cancel.
     * @param cleanup removes listeners, private entities and temporary items owned by this step
     * @return false when another provider already claimed it, or it has ended
     */
    public boolean claim(Runnable cleanup) {
        if (claimed || finished) return false;
        this.cleanup = Objects.requireNonNull(cleanup);
        claimed = true;
        return true;
    }
    /** @param success verified outcome; ignored after completion or cancellation */
    public void complete(boolean success) {
        if (!claimed || finished) return;
        finish();
        completion.accept(success);
    }
    /** Cancel silently; unlike completion this must not advance an abandoned guide. */
    public void cancel() {
        if (!finished) finish();
    }
    private void finish() {
        finished = true;
        Runnable release = cleanup;
        cleanup = null;
        if (release != null) release.run();
    }
    /** @return this event's handler list */
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    /** @return registration handler list required by Bukkit */
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
