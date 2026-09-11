package dev.stemcraft.service;

import dev.stemcraft.STEMCraft;
import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.command.CommandContext;
import org.bukkit.GameMode;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SelectionServiceImplTest {
    private STEMCraft plugin;
    private STEMCraftAPI api;
    private SelectionServiceImpl service;
    private PlayerMock player;
    private PlayerMock other;

    @BeforeEach void setup() {
        var server=MockBukkit.mock();
        player=server.addPlayer();other=server.addPlayer();
        player.setGameMode(GameMode.CREATIVE);
        plugin=mock(STEMCraft.class);
        when(plugin.namespace()).thenReturn("stemcraft");
        api=mock(STEMCraftAPI.class);
        service=spy(new SelectionServiceImpl(plugin,api));
    }

    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @Test void defaultsOnAndSavesOnlyThePlayersPreference() {
        assertTrue(service.isWorldEditPreviewEnabled(player));
        service.commandSelectionPreview(command("off"));
        assertFalse(service.isWorldEditPreviewEnabled(player));
        assertTrue(service.isWorldEditPreviewEnabled(other));
        assertFalse(new SelectionServiceImpl(plugin,api).isWorldEditPreviewEnabled(player));
        service.commandSelectionPreview(command("off"));
        assertFalse(service.isWorldEditPreviewEnabled(player));
        service.commandSelectionPreview(command("ON"));
        assertTrue(service.isWorldEditPreviewEnabled(player));
        service.commandSelectionPreview(command(""));
        assertFalse(service.isWorldEditPreviewEnabled(player));
        service.commandSelectionPreview(command(""));
        assertTrue(service.isWorldEditPreviewEnabled(player));
    }

    @Test void invalidArgumentsDoNotChangePreference() {
        var invalid=command("maybe");
        service.commandSelectionPreview(invalid);
        verify(invalid).returnUsage();
        assertTrue(service.isWorldEditPreviewEnabled(player));
        var extra=command("off");
        when(extra.args()).thenReturn(List.of("off","other-player"));
        service.commandSelectionPreview(extra);
        verify(extra).returnUsage();
        assertTrue(service.isWorldEditPreviewEnabled(player));
    }

    @Test void hiddenPreviewSkipsBothWorldEditLookupsAndLeavesOtherPlayersEnabled() throws Exception {
        service.commandSelectionPreview(command("off"));
        doReturn(null).when(service).getWorldEditPreviewSelection(other);
        doReturn(null).when(service).getWorldEditPrimaryPosition(other);
        var render=SelectionServiceImpl.class.getDeclaredMethod("renderWorldEditSelections");
        render.setAccessible(true);
        render.invoke(service);
        verify(service,never()).getWorldEditPreviewSelection(player);
        verify(service,never()).getWorldEditPrimaryPosition(player);
        verify(service).getWorldEditPreviewSelection(other);
        verify(service).getWorldEditPrimaryPosition(other);
        verify(service,never()).clearWorldEditSelection(any());
    }

    @Test void gridPreferenceIsIndependentAndPersists() {
        assertFalse(service.isWorldEditGridEnabled(player));
        service.commandSelectionPreview(command("grid", "on"));
        assertTrue(service.isWorldEditGridEnabled(player));
        assertFalse(service.isWorldEditGridEnabled(other));
        assertTrue(new SelectionServiceImpl(plugin,api).isWorldEditGridEnabled(player));
        service.commandSelectionPreview(command("off"));
        assertTrue(service.isWorldEditGridEnabled(player));
        service.commandSelectionPreview(command("grid", ""));
        assertFalse(service.isWorldEditGridEnabled(player));
        assertFalse(service.isWorldEditPreviewEnabled(player));
        var invalid=command("grid", "maybe");
        service.commandSelectionPreview(invalid);
        verify(invalid).returnUsage();
        assertFalse(service.isWorldEditGridEnabled(player));
    }

    @Test void gridThresholdUsesInclusiveSideLengths() {
        assertTrue(service.isGridWithinLimit(region(0,0,0,63,63,63)));
        assertFalse(service.isGridWithinLimit(region(0,0,0,64,1,1)));
        assertFalse(service.isGridWithinLimit(region(0,0,0,1,64,1)));
        assertFalse(service.isGridWithinLimit(region(0,0,0,1,1,64)));
    }

    @Test void previewIsBoundedNearbyAndCachedUntilSelectionOrViewerChanges() throws Exception {
        configureRenderer();
        var viewer=viewer();
        var region=region(-1000000,64,0,1000000,64,0);
        renderPreview(viewer,region);
        assertParticles(viewer,100);
        var cache=field("previewCache");
        Object first=((java.util.Map<?,?>)cache).get(viewer.getUniqueId());
        clearInvocations(viewer);
        renderPreview(viewer,region);
        assertSame(first,((java.util.Map<?,?>)cache).get(viewer.getUniqueId()));
        assertParticles(viewer,100);
        when(viewer.getLocation()).thenReturn(new org.bukkit.Location(player.getWorld(),3,64,0));
        renderPreview(viewer,region);
        assertNotSame(first,((java.util.Map<?,?>)cache).get(viewer.getUniqueId()));
        Object moved=((java.util.Map<?,?>)cache).get(viewer.getUniqueId());
        renderPreview(viewer,region(-10,64,0,10,64,0));
        assertNotSame(moved,((java.util.Map<?,?>)cache).get(viewer.getUniqueId()));
    }

    @Test void gridAutoHidesAndReappearsWithoutChangingPreference() throws Exception {
        configureRenderer();
        service.commandSelectionPreview(command("grid","on"));
        var viewer=viewer();
        renderPreview(viewer,region(0,64,0,64,64,1));
        var cache=(java.util.Map<?,?>)field("previewCache");
        Object large=cache.get(viewer.getUniqueId());
        service.commandSelectionPreview(command("grid","off"));
        renderPreview(viewer,region(0,64,0,64,64,1));
        assertEquals(large,cache.get(viewer.getUniqueId()));
        service.commandSelectionPreview(command("grid","on"));
        assertTrue(service.isWorldEditGridEnabled(player));
        renderPreview(viewer,region(0,64,0,10,65,10));
        Object small=cache.get(viewer.getUniqueId());
        service.commandSelectionPreview(command("grid","off"));
        renderPreview(viewer,region(0,64,0,10,65,10));
        assertNotEquals(small,cache.get(viewer.getUniqueId()));
    }

    @Test void disablingWorldEditDoesNotHideFeatureRegions() throws Exception {
        configureRenderer();
        service.commandSelectionPreview(command("off"));
        var viewer=viewer();
        service.showRegion(viewer,region(0,64,0,3,66,3));
        assertTrue(mockingDetails(viewer).getInvocations().stream()
            .anyMatch(call->call.getMethod().getName().equals("spawnParticle")));
    }

    private dev.stemcraft.api.model.SCRegion region(int x,int y,int z,int xx,int yy,int zz) {
        return new dev.stemcraft.api.model.SCRegion(new com.sk89q.worldedit.regions.CuboidRegion(
            com.sk89q.worldedit.math.BlockVector3.at(x,y,z),
            com.sk89q.worldedit.math.BlockVector3.at(xx,yy,zz)),player.getWorld());
    }

    private org.bukkit.entity.Player viewer() {
        var viewer=mock(org.bukkit.entity.Player.class);
        when(viewer.getWorld()).thenReturn(player.getWorld());
        when(viewer.getUniqueId()).thenReturn(player.getUniqueId());
        when(viewer.getLocation()).thenReturn(new org.bukkit.Location(player.getWorld(),0,64,0));
        when(viewer.getPersistentDataContainer()).thenReturn(player.getPersistentDataContainer());
        return viewer;
    }

    private void assertParticles(org.bukkit.entity.Player viewer,int budget) {
        var calls=mockingDetails(viewer).getInvocations().stream()
            .filter(call->call.getMethod().getName().equals("spawnParticle")).toList();
        assertFalse(calls.isEmpty());
        assertTrue(calls.size()<=budget,"Total particle calls exceed budget: "+calls.size());
        for (var call:calls) {
            var a=call.getArguments();
            var location=new org.bukkit.Location(viewer.getWorld(),(double)a[1],(double)a[2],(double)a[3]);
            assertTrue(location.distanceSquared(viewer.getLocation())<=48*48);
        }
    }

    private void renderPreview(org.bukkit.entity.Player viewer,dev.stemcraft.api.model.SCRegion region) throws Exception {
        var method=SelectionServiceImpl.class.getDeclaredMethod("renderWorldEditPreview",org.bukkit.entity.Player.class,dev.stemcraft.api.model.SCRegion.class);
        method.setAccessible(true);
        method.invoke(service,viewer,region);
    }

    private Object field(String name) throws Exception {
        var field=SelectionServiceImpl.class.getDeclaredField(name);field.setAccessible(true);return field.get(service);
    }

    private void configureRenderer() throws Exception {
        var values=java.util.Map.<String,Object>of("pointSpacing",2.0,"maxPoints",180,
            "majorMarkerInterval",10,"maxSurfacePoints",1000,"maxViewDistance",48.0,
            "maxSelectionSize",10_000_000L,"gridPointSpacing",5.0,"previewParticleBudget",100);
        for (var entry:values.entrySet()) {
            var field=SelectionServiceImpl.class.getDeclaredField(entry.getKey());field.setAccessible(true);field.set(service,entry.getValue());
        }
    }

    private CommandContext command(String first,String second) {
        var ctx=command(first);
        when(ctx.args()).thenReturn(second.isEmpty()?List.of(first):List.of(first,second));
        when(ctx.getArg(1, "")).thenReturn(second);
        return ctx;
    }

    private CommandContext command(String argument) {
        var ctx=mock(CommandContext.class);
        when(ctx.args()).thenReturn(argument.isEmpty()?List.of():List.of(argument));
        when(ctx.getArg(0, "")).thenReturn(argument);
        when(ctx.asPlayer()).thenReturn(player);
        return ctx;
    }
}
