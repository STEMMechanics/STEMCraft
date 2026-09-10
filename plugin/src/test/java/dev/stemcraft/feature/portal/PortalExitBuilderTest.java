package dev.stemcraft.feature.portal;

import dev.stemcraft.config.ConfigFileImpl;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.nio.file.*;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortalExitBuilderTest {
    @TempDir Path folder;
    ConfigFileImpl definitions;
    @BeforeEach void setup() throws Exception {
        MockBukkit.mock();
        Path file=folder.resolve("portals.yml");
        try(var resource=getClass().getClassLoader().getResourceAsStream("worlds/portals.yml")) {
            Files.copy(Objects.requireNonNull(resource),file);
        }
        definitions=new ConfigFileImpl(); assertTrue(definitions.load(file.toFile(),true));
        for(String id:new String[]{"deep","skylands","wasteland","faraway"}) {
            definitions.set("types."+id+".destination.search-radius",0);
            definitions.set("types."+id+".destination.link-radius",0);
        }
    }
    @AfterEach void cleanup() { MockBukkit.unmock(); }
    @Test void scalingUsesFloorAndRejectsInvalidRatios() {
        assertEquals(100,PortalExitBuilder.scaled(800,8));
        assertEquals(-100,PortalExitBuilder.scaled(-800,8));
        assertEquals(-1,PortalExitBuilder.scaled(-1,8));
        assertEquals(-2,PortalExitBuilder.scaled(-9,8));
        assertEquals(800,PortalExitBuilder.scaled(100,.125));
        definitions.set("types.deep.destination.coordinate-scale",0);
        assertThrows(IllegalArgumentException.class,()->type("deep"));
    }
    @Test void caveFloorIsFoundBelowTheRoofAndEmptyVoidIsRejected() {
        World cave=world(false);
        var underground=PortalExitBuilder.find(cave,type("deep"),-100,-200,0,65,at->false);
        assertNotNull(underground); assertEquals(31,underground.origin().y());
        assertTrue(underground.blocks().stream().allMatch(p->p.at().y()<100));
        for(int rotation=0;rotation<4;rotation++) {
            assertNull(PortalExitBuilder.find(world(true),type("skylands"),100,200,rotation,110,at->false));
        }
    }
    @Test void allPortalTypesBuildOnlyTheirConfiguredShapeOnNaturalGround() {
        for (String id : new String[]{"deep","wasteland","skylands","faraway"}) for (int rotation=0; rotation<4; rotation++) {
            var type=type(id);
            var plan=PortalExitBuilder.find(world(false),type,0,0,rotation,65,at->false);
            assertNotNull(plan,id); assertEquals(id.equals("skylands") ? 37 : 31,plan.origin().y());
            assertEquals(type.pattern().cells().size()+type.pattern().interior().size(),plan.blocks().size());
            long expectedStone=type.pattern().cells().stream().filter(c->c.material()==Material.SMOOTH_STONE).count();
            assertEquals(expectedStone,plan.blocks().stream().filter(p->p.data().getMaterial()==Material.SMOOTH_STONE).count());
            assertTrue(plan.blocks().stream().allMatch(p->p.at().y()>=plan.origin().y()));
        }
    }
    @Test void skylandsDropHasFiveClearBlocksAndRechecksLandingHazards() {
        var type=type("skylands");
        for(int rotation=0;rotation<4;rotation++) {
            World terrain=world(false);
            var plan=PortalExitBuilder.find(terrain,type,-16,-16,rotation,65,at->false);
            assertNotNull(plan); assertEquals(37,plan.origin().y());
            Location arrival=AirDropExit.landing(terrain,type,plan.origin(),rotation,at->true);
            assertNotNull(arrival); assertEquals(35,arrival.getBlockY());
            for(int y=32;y<37;y++) assertTrue(terrain.getBlockAt(arrival.getBlockX(),y,arrival.getBlockZ()).getType().isAir());
            Block hazard=mock(Block.class); when(hazard.getType()).thenReturn(Material.LAVA);
            when(terrain.getBlockAt(arrival.getBlockX(),31,arrival.getBlockZ())).thenReturn(hazard);
            assertNull(AirDropExit.landing(terrain,type,plan.origin(),rotation,at->true));
            when(hazard.getType()).thenReturn(Material.WATER);
            assertNotNull(AirDropExit.landing(terrain,type,plan.origin(),rotation,at->true));
            when(terrain.getBlockAt(arrival.getBlockX(),34,arrival.getBlockZ())).thenReturn(hazard);
            assertNull(AirDropExit.landing(terrain,type,plan.origin(),rotation,at->true));
        }
    }
    @Test void solidObstructionDoesNotCauseAnElevatedPlatform() {
        World terrain = world(false);
        Block raised = mock(Block.class); when(raised.getType()).thenReturn(Material.STONE);
        when(terrain.getBlockAt(1,33,0)).thenReturn(raised);
        assertNull(PortalExitBuilder.find(terrain,type("deep"),0,0,0,32,at->false));
    }
    @Test void landingFacesOutwardForEveryRotationAndBothSides() {
        for(String id:new String[]{"deep","wasteland","skylands","faraway"}) for(int rotation=0;rotation<4;rotation++) {
            var type=type(id); var origin=new PortalPattern.Offset(-100,31,-200);
            var b=PortalExitBuilder.bounds(type.pattern(),rotation);
            double cx=origin.x()+(b.minX()+b.maxX())/2.0+.5,cz=origin.z()+(b.minZ()+b.maxZ())/2.0+.5;
            for(int side:new int[]{-1,1}) {
                Location at=new Location(null,cx+side*3,33,cz+side*3,180,70);
                PortalExitBuilder.faceAway(at,type.pattern(),origin,rotation);
                var outward=new org.bukkit.util.Vector(at.getX()-cx,0,at.getZ()-cz);
                assertTrue(at.getDirection().dot(outward)>0,id);
                assertEquals(0,at.getPitch());
            }
        }
    }
    @Test void reservedLandAndWorldBorderPreventPlacement() {
        World world=world(true);
        assertNull(PortalExitBuilder.find(world,type("deep"),0,0,0,65,at->true));
        when(world.getWorldBorder().isInside(any(Location.class))).thenReturn(false);
        assertNull(PortalExitBuilder.find(world,type("deep"),0,0,0,65,at->false));
    }
    @Test void clearanceChecksNeverReplaceContainersOrSolidObstacles() {
        World world=world(true);
        Block chest=mock(Block.class); when(chest.getType()).thenReturn(Material.CHEST);
        when(world.getBlockAt(0,65,0)).thenReturn(chest);
        var type=type("deep");
        assertNull(PortalExitBuilder.at(world,type,new PortalPattern.Offset(0,65,0),0,
                PortalExitBuilder.bounds(type.pattern(),0),at->false));
        verify(chest,never()).setBlockData(any(),anyBoolean());
    }
    private SurvivalPortalType type(String id) { return SurvivalPortalType.read(id,definitions.getSection("types."+id)); }
    private World world(boolean empty) {
        World world=mock(World.class);
        when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        WorldBorder border=mock(WorldBorder.class); when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(any(Location.class))).thenReturn(true);
        Block air=mock(Block.class),stone=mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR); when(stone.getType()).thenReturn(Material.STONE);
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(call->{
            int y=call.getArgument(1); return !empty && (y<32 || y>=100) ? stone : air;
        });
        return world;
    }
}
