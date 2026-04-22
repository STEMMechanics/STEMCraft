package dev.stemcraft.api.serialize;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import dev.stemcraft.api.model.SCRegion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionSerializerTest {
    private WorldMock world;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        world = server.addSimpleWorld("serializer-tests");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void serializesAndDeserializesCuboidRegions() {
        SCRegion region = new SCRegion(new CuboidRegion(
            BlockVector3.at(0, 60, 0),
            BlockVector3.at(3, 64, 3)
        ), world);

        Map<String, Object> serialized = RegionSerializer.serialize(region);
        assertEquals("serializer-tests", serialized.get("world"));
        assertEquals("CUBOID", serialized.get("type"));

        SCRegion deserialized = RegionSerializer.deserialize(serialized);
        assertNotNull(deserialized);
        assertEquals(region.toString(), deserialized.toString());
        assertEquals(region.getWorld(), deserialized.getWorld());

        String compact = RegionSerializer.toString(region);
        assertEquals("CUBOID:0,60,0,3,64,3", compact);
        assertEquals(compact, RegionSerializer.fromString(compact, world).toString());
    }

    @Test
    void serializesAndDeserializesPolygonRegions() {
        SCRegion region = new SCRegion(new Polygonal2DRegion(
            null,
            List.of(
                BlockVector2.at(0, 0),
                BlockVector2.at(5, 0),
                BlockVector2.at(5, 5),
                BlockVector2.at(0, 5)
            ),
            60,
            70
        ), world);

        Map<String, Object> serialized = RegionSerializer.serialize(region);
        assertEquals("POLYGON", serialized.get("type"));
        assertTrue(serialized.containsKey("points"));

        SCRegion deserialized = RegionSerializer.deserialize(serialized);
        assertNotNull(deserialized);
        assertEquals(region.toString(), deserialized.toString());
        assertEquals(region.getPolygonVertices().size(), deserialized.getPolygonVertices().size());

        String compact = RegionSerializer.toString(region);
        assertEquals(compact, RegionSerializer.fromString(compact, world).toString());
    }

    @Test
    void deserializeReturnsNullWhenWorldCannotBeResolved() {
        Map<String, Object> serialized = new LinkedHashMap<>();
        serialized.put("world", "missing");
        serialized.put("type", "CUBOID");
        serialized.put("min", Map.of("x", 0, "y", 60, "z", 0));
        serialized.put("max", Map.of("x", 1, "y", 61, "z", 1));

        assertNull(RegionSerializer.deserialize(serialized));
        assertNull(RegionSerializer.fromString("CUBOID:0,60,0,1,61,1", null));
    }

    @Test
    void deserializeRejectsInvalidRegionShapes() {
        IllegalArgumentException unknownType = assertThrows(
            IllegalArgumentException.class,
            () -> RegionSerializer.deserialize(Map.of("world", world.getName(), "type", "UNKNOWN"))
        );
        assertEquals("Unknown region type: UNKNOWN", unknownType.getMessage());

        IllegalArgumentException missingPoints = assertThrows(
            IllegalArgumentException.class,
            () -> RegionSerializer.deserialize(Map.of(
                "world", world.getName(),
                "type", "POLYGON",
                "minY", 60,
                "maxY", 70,
                "points", List.of()
            ))
        );
        assertEquals("Missing points for polygon region", missingPoints.getMessage());

        IllegalArgumentException invalidString = assertThrows(
            IllegalArgumentException.class,
            () -> RegionSerializer.fromString("BAD", world)
        );
        assertEquals("Invalid region string: BAD", invalidString.getMessage());
    }
}
