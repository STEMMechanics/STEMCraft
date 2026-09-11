package dev.stemcraft.permission;

import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PlayerCommandAccessTest {
    private final Set<String> grants = new HashSet<>();
    private Player player() {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Alice");
        when(player.hasPermission(anyString())).thenAnswer(call -> grants.contains(call.getArgument(0)));
        return player;
    }

    @ParameterizedTest
    @ValueSource(strings = {"bedwars", "bridge", "boatrace", "minefield", "nightfall", "parkour", "tntrun", "skyblock"})
    void everyGameSeparatesPlayOthersAndAdministration(String game) {
        Player player = player();
        String prefix = "stemcraft.minigame." + game;
        assertFalse(PlayerCommandAccess.minigame(player,List.of("join","arena"),game));
        grants.add(prefix + ".play");
        List<String> self = game.equals("skyblock") ? List.of("join") : List.of("join","arena");
        List<String> other = game.equals("skyblock") ? List.of("join","Bob") : List.of("join","arena","Bob");
        assertTrue(PlayerCommandAccess.minigame(player,self,game));
        assertTrue(PlayerCommandAccess.minigame(player,List.of("leave"),game));
        assertFalse(PlayerCommandAccess.minigame(player,other,game));
        assertFalse(PlayerCommandAccess.minigame(player,List.of("leave","Bob"),game));
        for (String sub : List.of("delete","create","set","reload","joinall","start","stop","pool","cycle"))
            assertFalse(PlayerCommandAccess.minigame(player,List.of(sub,"arena"),game),game+" "+sub);
        grants.add(prefix + ".others");
        assertTrue(PlayerCommandAccess.minigame(player,other,game));
        assertFalse(PlayerCommandAccess.minigame(player,List.of("delete","arena"),game));
        grants.clear(); grants.add(prefix + ".admin");
        assertTrue(PlayerCommandAccess.minigame(player,List.of("delete","arena"),game));
        grants.clear(); grants.add("stemcraft.command."+game);
        assertTrue(PlayerCommandAccess.minigame(player,List.of("delete","arena"),game));
        assertTrue(PlayerCommandAccess.minigame(mock(ConsoleCommandSender.class),other,game));
    }

    @Test void personalRestartAndResetDoNotGrantArenaRestart() {
        Player player = player();
        grants.addAll(List.of("stemcraft.minigame.parkour.play","stemcraft.minigame.skyblock.play","stemcraft.minigame.nightfall.play"));
        assertTrue(PlayerCommandAccess.minigame(player,List.of("restart"),"parkour"));
        assertFalse(PlayerCommandAccess.minigame(player,List.of("restart","Bob"),"parkour"));
        assertFalse(PlayerCommandAccess.minigame(player,List.of("restart","arena"),"nightfall"));
        assertFalse(PlayerCommandAccess.minigame(player,List.of("reset"),"skyblock"));
        grants.add("stemcraft.minigame.skyblock.reset");
        assertTrue(PlayerCommandAccess.minigame(player,List.of("reset"),"skyblock"));
    }

    @Test void sendingMailDoesNotExposeAdministrativeQueueActions() {
        Player player=player();grants.add("stemcraft.mailbox.send");
        assertTrue(PlayerCommandAccess.mailbox(player,List.of("send","Bob","Hello")));
        for (String sub : List.of("", "queue","view","delete","release","hold","item"))
            assertFalse(PlayerCommandAccess.mailbox(player,List.of(sub)));
    }

    @Test void noticeOwnershipAndReadAccessAreDistinct() {
        Player player=player(); UUID owner=UUID.randomUUID();when(player.getUniqueId()).thenReturn(owner);
        grants.addAll(List.of("stemcraft.noticeboard.read","stemcraft.noticeboard.post","stemcraft.namedregion.read"));
        assertTrue(PlayerCommandAccess.noticeboard(player,List.of("edit","post")));
        assertFalse(PlayerCommandAccess.noticeboard(player,List.of("expiry","post","-1")));
        assertFalse(PlayerCommandAccess.noticeboard(player,List.of("board","delete","board")));
        assertTrue(PlayerCommandAccess.owns(player,owner,"stemcraft.noticeboard.post","stemcraft.noticeboard.admin"));
        assertFalse(PlayerCommandAccess.owns(player,UUID.randomUUID(),"stemcraft.noticeboard.post","stemcraft.noticeboard.admin"));
        grants.remove("stemcraft.noticeboard.post");
        assertFalse(PlayerCommandAccess.owns(player,owner,"stemcraft.noticeboard.post","stemcraft.noticeboard.admin"));
        assertTrue(PlayerCommandAccess.namedRegion(player,List.of("find","Forest")));
        assertFalse(PlayerCommandAccess.namedRegion(player,List.of("rename","area","New")));
        assertFalse(PlayerCommandAccess.namedRegion(player,List.of("teleport","area")));
    }

    @Test void selfTargetsAreAllowedButOtherPlayersAndWorldsRequireExtraGrants() {
        Player player=player();World world=mock(World.class);when(world.getName()).thenReturn("creative");when(player.getWorld()).thenReturn(world);
        assertTrue(PlayerCommandAccess.ownTarget(player,List.of("ALICE"),0,"example.others"));
        assertFalse(PlayerCommandAccess.ownTarget(player,List.of("Bob"),0,"example.others"));
        assertTrue(PlayerCommandAccess.spawn(player,List.of()));
        assertTrue(PlayerCommandAccess.spawn(player,List.of("creative")));
        assertFalse(PlayerCommandAccess.spawn(player,List.of("survival")));
        assertFalse(PlayerCommandAccess.spawn(player,List.of("creative","Bob")));
        grants.add("stemcraft.command.spawn.world.survival");
        assertTrue(PlayerCommandAccess.spawn(player,List.of("survival")));
        assertFalse(PlayerCommandAccess.spawn(player,List.of("survival","Bob")));
        assertTrue(PlayerCommandAccess.speed(player,List.of("fly","2")));
        assertFalse(PlayerCommandAccess.speed(player,List.of("fly","2","Bob")));
        assertFalse(PlayerCommandAccess.speed(player,List.of("fly","2","12345")));
        assertFalse(PlayerCommandAccess.speed(player,List.of("2","12345")));
        assertFalse(PlayerCommandAccess.book(player,List.of("show","rules","Bob")));
        assertFalse(PlayerCommandAccess.book(player,List.of("del","rules")));
    }
}
