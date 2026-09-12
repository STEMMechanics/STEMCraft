package dev.stemcraft.api.event.guide;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class GuideActionRequestEventTest {
    @Test void onlyOneProviderClaimsAndCompletionReleasesExactlyOnce() {
        var result = new ArrayList<Boolean>();
        var cleanup = new AtomicInteger();
        var event = new GuideActionRequestEvent(mock(Player.class), "stemcraft:quest-practice", result::add);
        assertTrue(event.claim(cleanup::incrementAndGet));
        assertFalse(event.claim(() -> fail("second provider must not own cleanup")));
        event.complete(true);
        event.complete(false);
        event.cancel();
        assertEquals(java.util.List.of(true), result);
        assertEquals(1, cleanup.get());
    }
    @Test void cancellationDiscardsLateSuccessWithoutAdvancingTheGuide() {
        var event = new GuideActionRequestEvent(mock(Player.class), "stemcraft:coordbar", value -> fail("cancelled callback"));
        var cleanup = new AtomicInteger();
        event.claim(cleanup::incrementAndGet);
        event.cancel();
        event.complete(true);
        assertTrue(event.finished());
        assertEquals(1, cleanup.get());
    }
}
