package dev.stemcraft.service.resourcepack;

import dev.stemcraft.api.config.ConfigSectionView;
import dev.stemcraft.api.service.resourcepack.PackFormatRange;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourcePackServiceImplTest {

    @Test
    void resolveResourcePackFormatMatchesKnownVersionThresholds() {
        assertEquals(32, ResourcePackServiceImpl.resolveResourcePackFormat(new int[] {1, 20, 6}));
        assertEquals(34, ResourcePackServiceImpl.resolveResourcePackFormat(new int[] {1, 21, 0}));
        assertEquals(42, ResourcePackServiceImpl.resolveResourcePackFormat(new int[] {1, 21, 3}));
        assertEquals(46, ResourcePackServiceImpl.resolveResourcePackFormat(new int[] {1, 21, 4}));
        assertEquals(55, ResourcePackServiceImpl.resolveResourcePackFormat(new int[] {1, 21, 5}));
        assertEquals(63, ResourcePackServiceImpl.resolveResourcePackFormat(new int[] {1, 21, 6}));
        assertEquals(64, ResourcePackServiceImpl.resolveResourcePackFormat(new int[] {1, 21, 8}));
        assertEquals(69, ResourcePackServiceImpl.resolveResourcePackFormat(new int[] {1, 21, 10}));
        assertEquals(75, ResourcePackServiceImpl.resolveResourcePackFormat(new int[] {1, 21, 11}));
    }

    @Test
    void resolveResourcePackFormatFallsBackToEarliestKnownFormat() {
        assertEquals(32, ResourcePackServiceImpl.resolveResourcePackFormat(null));
        assertEquals(32, ResourcePackServiceImpl.resolveResourcePackFormat(new int[] {1, 19, 4}));
        assertEquals(32, ResourcePackServiceImpl.resolveResourcePackFormat(new int[] {0, 0, 0}));
    }

    @Test
    void resolveSupportedVersionRangeStartsAtEarliestKnownAndClampsToCurrentVersion() {
        assertEquals(32, ResourcePackServiceImpl.resolveSupportedVersionRange(new int[] {1, 21, 11})[0]);
        assertEquals(75, ResourcePackServiceImpl.resolveSupportedVersionRange(new int[] {1, 21, 11})[1]);
        assertEquals(32, ResourcePackServiceImpl.resolveSupportedVersionRange(new int[] {1, 21, 3})[0]);
        assertEquals(42, ResourcePackServiceImpl.resolveSupportedVersionRange(new int[] {1, 21, 3})[1]);
    }

    @Test
    void resolveSupportedVersionRangeUsesConfiguredMinButKeepsCurrentMinecraftAsMax() {
        ConfigSectionView config = mock(ConfigSectionView.class);
        when(config.getInt("min_pack_format", 32)).thenReturn(65);
        when(config.getInt("max_pack_format", 75)).thenReturn(69);

        List<String> warnings = new ArrayList<>();
        int[] supportedRange = ResourcePackServiceImpl.resolveSupportedVersionRange(
            new int[] {1, 21, 11},
            config,
            warnings::add
        );

        assertEquals(65, supportedRange[0]);
        assertEquals(75, supportedRange[1]);
        assertEquals(1, warnings.size());
    }

    @Test
    void resolveSupportedVersionRangeClampsConfiguredMinToCurrentMinecraftFormat() {
        ConfigSectionView config = mock(ConfigSectionView.class);
        when(config.getInt("min_pack_format", 32)).thenReturn(80);
        when(config.getInt("max_pack_format", 75)).thenReturn(75);

        List<String> warnings = new ArrayList<>();
        int[] supportedRange = ResourcePackServiceImpl.resolveSupportedVersionRange(
            new int[] {1, 21, 11},
            config,
            warnings::add
        );

        assertEquals(75, supportedRange[0]);
        assertEquals(75, supportedRange[1]);
        assertEquals(1, warnings.size());
    }

    @Test
    void planFutureSegmentsSplitsRangesWhereGeneratorCompatibilityChanges() {
        List<PackFormatRange> plannedRanges = ResourcePackServiceImpl.planFutureSegments(
            75,
            List.of(
                List.of(new PackFormatRange(64, 75)),
                List.of(new PackFormatRange(64, 75), new PackFormatRange(80, 85)),
                List.of(new PackFormatRange(64, 82))
            )
        );

        assertEquals(3, plannedRanges.size());
        assertEquals(new PackFormatRange(76, 79), plannedRanges.get(0));
        assertEquals(new PackFormatRange(80, 82), plannedRanges.get(1));
        assertEquals(new PackFormatRange(83, 85), plannedRanges.get(2));
    }
}
