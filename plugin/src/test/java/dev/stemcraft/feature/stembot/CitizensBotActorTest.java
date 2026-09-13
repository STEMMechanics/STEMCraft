package dev.stemcraft.feature.stembot;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CitizensBotActorTest {
    @Test
    void recoveryUsesDirectMovementWhileNormalTargetsUsePathfinding() {
        TestNavigator navigator = new TestNavigator();
        Location destination = new Location(null, -871, 85, 346);
        Location firstWaypoint = new Location(null, -817.5, 70, 302.5);
        CitizensBotActor.targetNavigator(navigator, destination, .9, false);
        assertEquals(destination, navigator.plannedTarget);
        assertNull(navigator.directTarget);
        assertEquals(.9f, navigator.parameters.speed);

        CitizensBotActor.targetNavigator(navigator, firstWaypoint, .8, true);
        assertEquals(firstWaypoint, navigator.directTarget);
        assertEquals(destination, navigator.plannedTarget);
        assertEquals(.8f, navigator.parameters.speed);
    }

    public static final class TestNavigator {
        final TestParameters parameters = new TestParameters();
        Location plannedTarget;
        Location directTarget;
        public TestParameters getDefaultParameters() { return parameters; }
        public void setTarget(Location target) { plannedTarget = target; }
        public void setStraightLineTarget(Location target) { directTarget = target; }
    }

    public static final class TestParameters {
        float speed;
        public void speedModifier(float value) { speed = value; }
    }
}
