package dev.stemcraft.api.util;

import dev.stemcraft.api.minigame.util.TeamNames;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiUtilityResultTest {
    @TempDir
    Path tempDir;

    private WorldMock world;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        world = server.addSimpleWorld("api-tests");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void byteFormatFormatsAndParsesValues() {
        assertEquals("512 B", ByteFormat.formatBytes(512));
        assertEquals("1.50 KB", ByteFormat.formatBytes(1536));
        assertEquals("1.00 PB", ByteFormat.formatBytes(1125899906842624L));

        assertEquals(512L, ByteFormat.toBytes("512"));
        assertEquals(1536L, ByteFormat.toBytes("1.5 KB"));
        assertEquals(1610612736L, ByteFormat.toBytes("1.5 GB"));
        assertThrows(IllegalArgumentException.class, () -> ByteFormat.toBytes(""));
        assertThrows(IllegalArgumentException.class, () -> ByteFormat.toBytes("ten mb"));
    }

    @Test
    void patternUtilConvertsGlobToAnchoredRegex() {
        Pattern pattern = PatternUtil.globToRegex("file-*.txt");

        assertTrue(pattern.matcher("file-report.txt").matches());
        assertFalse(pattern.matcher("other-report.txt").matches());
        assertFalse(pattern.matcher("file-report.txt.bak").matches());
    }

    @Test
    void mapParseReadsTypedValuesAndReportsLogicalPaths() {
        UUID uuid = UUID.randomUUID();
        Map<String, Object> nested = Map.of("name", "inner");
        Map<String, Object> map = Map.of(
            "string", "value",
            "int", "42",
            "long", 44L,
            "double", "12.5",
            "bool", "yes",
            "uuid", uuid.toString(),
            "map", nested,
            "list", List.of(nested)
        );

        assertEquals("value", MapParse.string(map, "string", "root"));
        assertEquals(42, MapParse.requireInt(map, "int", "root"));
        assertEquals(44L, MapParse.longValue(map, "long", "root"));
        assertEquals(12.5d, MapParse.doubleValue(map, "double", "root"));
        assertEquals(true, MapParse.bool(map, "bool", "root"));
        assertEquals(uuid, MapParse.uuid(map, "uuid", "root"));
        assertEquals(nested, MapParse.requireMap(map, "map", "root"));
        assertEquals(List.of(nested), MapParse.listOfMaps(map.get("list"), "root.list"));
        assertEquals(List.of(), MapParse.list(null, "root.missing"));

        IllegalArgumentException typeError = assertThrows(
            IllegalArgumentException.class,
            () -> MapParse.requireString(Map.of("count", 1), "count", "root")
        );
        assertEquals("Expected string at root.count, got Integer", typeError.getMessage());

        IllegalArgumentException missingError = assertThrows(
            IllegalArgumentException.class,
            () -> MapParse.requireList(Map.of(), "players", "root")
        );
        assertEquals("Missing value at root.players", missingError.getMessage());
    }

    @Test
    void namespaceIdValidatesNormalizesAndExtractsParts() {
        assertTrue(NamespaceId.isValid("stemcraft:minigames/arena_1"));
        assertTrue(NamespaceId.isValid("stemcraft:minigames/arena-1"));
        assertFalse(NamespaceId.isValid("StemCraft:arena"));
        assertEquals("stemcraft:arena_1", NamespaceId.normalize("STEMCRAFT:ARENA_1"));
        assertEquals("arena/path_name", NamespaceId.sanitizePath(" /Arena//Path Name!/ "));
        assertEquals("stemcraft:arena", NamespaceId.of("stemcraft", "arena"));
        assertEquals("stemcraft", NamespaceId.getNamespace("stemcraft:arena/one"));
        assertEquals("arena/one", NamespaceId.getPath("stemcraft:arena/one"));
        assertArrayEquals(new String[]{"arena", "one"}, NamespaceId.getPathSegments("stemcraft:arena/one"));
        assertThrows(IllegalArgumentException.class, () -> NamespaceId.checkValid("bad namespace"));
    }

    @Test
    void placeholderUtilAppliesPlaceholdersAndPreservesRequestedCaseStyle() {
        assertEquals("Hello Alex", PlaceholderUtil.apply("Hello {Name}", Map.of("Name", "alex")));
        assertEquals("Hello ALEX", PlaceholderUtil.apply("Hello {NAME}", "NAME", "alex"));
        assertEquals("Hello alex", PlaceholderUtil.apply("Hello {name}", "name", "alex"));
        assertEquals("Hello world", PlaceholderUtil.apply("Hello {target}", "target", "world", "unused"));
    }

    @Test
    void stringUtilCoversFormattingPluralisationAndParsingHelpers() {
        assertTrue(StringUtil.isAllUpper("NASA"));
        assertTrue(StringUtil.isTitleCase("Hello"));
        assertEquals("Hello", StringUtil.toTitleCase("hELLO"));
        assertEquals("hello_world", StringUtil.slugify(" Hello, World! "));
        assertEquals("camel_case", StringUtil.toSnakeCase("camelCase"));
        assertEquals("camel_case", StringUtil.camelToSnake("camelCase"));
        assertEquals("camel-case", StringUtil.camelToKebab("camelCase"));
        assertArrayEquals(new String[]{"1", "null", "value"}, StringUtil.toStrings(1, null, "value"));
        assertEquals("&aHello &bWorld", StringUtil.capitalize("&ahello &bworld", true));
        assertEquals("hello world", StringUtil.beautify("HELLO_WORLD"));
        assertEquals("children", StringUtil.toPlural("child"));
        assertEquals("game worlds", StringUtil.toPlural("game world"));
        assertEquals("Cities", StringUtil.toPlural("City"));
        assertTrue(StringUtil.parseBoolean(" YES "));
        assertFalse(StringUtil.parseBoolean("off"));
        assertEquals("one | two", StringUtil.joinPlainText(List.of(Component.text("one"), Component.text("two")), " | "));
        assertTrue(StringUtil.isInteger("-42"));
        assertFalse(StringUtil.isInteger("4.2"));
        assertEquals("\\\"quote\\\"", StringUtil.escapeJson("\"quote\""));
    }

    @Test
    void textUtilConvertsAndStripsFormatting() {
        assertEquals("Hello", TextUtil.stripColour("<green>Hello</green>"));
        assertEquals("§aHello", TextUtil.colouriseToSection("&aHello"));
        assertEquals("&aHello", TextUtil.colouriseToAmpersand("§aHello"));
        assertEquals(8, TextUtil.componentLength(Component.text("Hi there")));
        assertEquals("plain", TextUtil.plain(Component.text("plain")));
        assertArrayEquals(new String[]{"§aOne", ""}, TextUtil.colourise("&aOne", null));
    }

    @Test
    void timeUtilFormatsParsesAndValidatesDates() {
        assertEquals("1d 2h 3m 4s", TimeUtil.formatDuration(93784, TimeUtil.FormatStyle.SHORT));
        assertEquals("1 day 2 hours 3 mins 4 secs", TimeUtil.formatLongDuration(93784));
        assertEquals("1 day 2 hours 3 minutes 4 seconds", TimeUtil.formatFriendlyDuration(93784));
        assertEquals(93784L, TimeUtil.parseDuration("1d2h3m4s"));
        assertEquals(-1L, TimeUtil.parseDuration("perm", true));
        assertThrows(IllegalArgumentException.class, () -> TimeUtil.parseDuration("10x"));
        assertTrue(TimeUtil.validDate("2026-04-21"));
        assertFalse(TimeUtil.validDate("2026-02-30"));

        String expectedTimestamp = Instant.ofEpochMilli(0)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        assertEquals(expectedTimestamp, TimeUtil.toTimestamp(0));

        long start = Instant.now().plus(Duration.ofSeconds(5)).toEpochMilli();
        long runAt = TimeUtil.durationToRunAtMillis(Duration.ofSeconds(5));
        long end = Instant.now().plus(Duration.ofSeconds(5)).toEpochMilli();
        assertTrue(runAt >= start - 100);
        assertTrue(runAt <= end + 100);
    }

    @Test
    void directionUtilMapsYawToCompassDirections() {
        assertEquals("S", DirectionUtil.getCompassDirection(0));
        assertEquals("W", DirectionUtil.getCompassDirection(90));
        assertEquals("N", DirectionUtil.getCompassDirection(180));
        assertEquals("E", DirectionUtil.getCompassDirection(270));
    }

    @Test
    void fileUtilCopiesHashesAndDeletesFiles() throws Exception {
        Path sourceFile = tempDir.resolve("source/file.txt");
        Path copiedFile = tempDir.resolve("copied/file.txt");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, "alpha");

        FileUtil.copyFile(sourceFile, copiedFile);
        assertEquals("alpha", Files.readString(copiedFile));

        Path sourceDir = tempDir.resolve("source-dir");
        Path nestedSource = sourceDir.resolve("nested/value.txt");
        Files.createDirectories(nestedSource.getParent());
        Files.writeString(nestedSource, "first");

        Path destDir = tempDir.resolve("dest-dir");
        FileUtil.copyDirectory(sourceDir, destDir, false);
        assertEquals("first", Files.readString(destDir.resolve("nested/value.txt")));

        Files.writeString(nestedSource, "second");
        FileUtil.copyDirectory(sourceDir, destDir, false);
        assertEquals("first", Files.readString(destDir.resolve("nested/value.txt")));

        FileUtil.copyDirectory(sourceDir, destDir, true);
        assertEquals("second", Files.readString(destDir.resolve("nested/value.txt")));

        assertEquals("be76331b95dfc399cd776d2fc68021e0db03cc4f", FileUtil.sha1Hex(sourceFile.toFile()));

        File oldest = tempDir.resolve("latest/old.txt").toFile();
        File newest = tempDir.resolve("latest/new.txt").toFile();
        Files.createDirectories(oldest.getParentFile().toPath());
        Files.writeString(oldest.toPath(), "old");
        Files.writeString(newest.toPath(), "new");
        Files.setLastModifiedTime(oldest.toPath(), FileTime.fromMillis(10));
        Files.setLastModifiedTime(newest.toPath(), FileTime.fromMillis(20));
        Files.setLastModifiedTime(tempDir.resolve("latest"), FileTime.fromMillis(0));
        assertEquals(20L, new FileUtil().getLatestModified(tempDir.resolve("latest").toFile()));

        File deleteDir = tempDir.resolve("delete-me").toFile();
        Files.createDirectories(deleteDir.toPath().resolve("nested"));
        Files.writeString(deleteDir.toPath().resolve("nested/file.txt"), "value");
        FileUtil.deleteRecursive(deleteDir);
        assertFalse(deleteDir.exists());
    }

    @Test
    void locationUtilSerializesAndDeserializesLocations() {
        Location location = new Location(world, 1.5, 64, -2.25f, 90f, 45f);
        String serialized = LocationUtil.serialize(location, true, true);

        assertEquals("api-tests,1.5,64.0,-2.25,90.0,45.0", serialized);

        Location explicitWorld = LocationUtil.deserialize(serialized);
        assertNotNull(explicitWorld);
        assertEquals(world.getName(), explicitWorld.getWorld().getName());
        assertEquals(90f, explicitWorld.getYaw());
        assertEquals(45f, explicitWorld.getPitch());

        Location defaultWorld = LocationUtil.deserialize("1,2,3", world);
        assertNotNull(defaultWorld);
        assertEquals(world, defaultWorld.getWorld());
        assertNull(LocationUtil.deserialize("missing-world,1,2,3"));
        assertNull(LocationUtil.deserialize("1,2"));
    }

    @Test
    void fontUtilCalculatesPixelWidthWithFormattingAndCustomGlyphs() {
        assertEquals(9, FontUtil.calculatePixelWidth("il "));
        assertEquals(8, FontUtil.calculatePixelWidth("§aHi"));
        assertEquals(10, FontUtil.calculatePixelWidth("§lHi"));
        assertEquals(15, FontUtil.calculatePixelWidth("§l■★"));
        assertEquals(10, FontUtil.calculatePixelWidth(Component.text("Hi").decorate(TextDecoration.BOLD)));
        assertEquals(9, FontUtil.calculatePixelWidth(Component.text("il ")));
    }

    @Test
    void inventoryUtilFormatsInventoryContentsOrEmptyState() {
        Inventory empty = Bukkit.createInventory(null, 9);
        assertEquals("(empty)", InventoryUtil.toString(empty));

        Inventory inventory = Bukkit.createInventory(null, 9);
        inventory.addItem(new org.bukkit.inventory.ItemStack(Material.DIAMOND, 3));
        inventory.addItem(new org.bukkit.inventory.ItemStack(Material.STONE, 2));
        assertEquals("DIAMOND x3, STONE x2", InventoryUtil.toString(inventory));
    }

    @Test
    void worldTimeUtilConvertsTicksToHumanReadableClock() {
        world.setTime(0L);
        assertEquals("6:00 AM", WorldTimeUtil.toClock(world));

        world.setTime(6249L);
        assertEquals("12:14 PM", WorldTimeUtil.toClock(world));
        assertEquals("12:00 PM", WorldTimeUtil.toClockQuarter(world));
    }

    @Test
    void worldUtilResolvesBaseNamesAndEnvironments() {
        assertEquals("hub", WorldUtil.baseName("hub_nether"));
        assertEquals("hub", WorldUtil.baseName("hub_the_end"));
        assertEquals("hub", WorldUtil.baseName("hub"));
        assertEquals("api-tests", WorldUtil.baseName(world));
        assertEquals(World.Environment.NETHER, WorldUtil.resolveEnvironment("hub_nether"));
        assertEquals(World.Environment.THE_END, WorldUtil.resolveEnvironment("hub_end"));
        assertEquals(World.Environment.NORMAL, WorldUtil.resolveEnvironment("hub"));
    }

    @Test
    void teamNamesNormalizeValidateAndMapMaterials() {
        assertTrue(TeamNames.isPredefinedName("Red"));
        assertFalse(TeamNames.isPredefinedName("custom"));
        assertEquals("blue", TeamNames.normalize("Blue"));
        assertEquals(Material.RED_WOOL, TeamNames.getMaterial("red"));
        assertEquals(Material.WHITE_WOOL, TeamNames.getMaterial("custom"));
        assertEquals(
            java.util.Set.of(
                TeamNames.TEAM_ORANGE,
                TeamNames.TEAM_MAGENTA,
                TeamNames.TEAM_LIGHT_BLUE,
                TeamNames.TEAM_YELLOW,
                TeamNames.TEAM_LIME,
                TeamNames.TEAM_PINK,
                TeamNames.TEAM_GRAY,
                TeamNames.TEAM_LIGHT_GRAY,
                TeamNames.TEAM_CYAN,
                TeamNames.TEAM_PURPLE,
                TeamNames.TEAM_BLUE,
                TeamNames.TEAM_BROWN,
                TeamNames.TEAM_GREEN,
                TeamNames.TEAM_RED,
                TeamNames.TEAM_BLACK,
                TeamNames.TEAM_WHITE,
                TeamNames.TEAM_AUTO
            ),
            TeamNames.predefined()
        );
    }
}
