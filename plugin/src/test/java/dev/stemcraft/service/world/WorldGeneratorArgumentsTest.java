package dev.stemcraft.service.world;

import dev.stemcraft.api.command.Command;
import dev.stemcraft.service.command.CommandContextImpl;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldGeneratorArgumentsTest {
    @Test void namespacedIdsAndColonOptionsRemainPositional() {
        var ctx = new CommandContextImpl(mock(Command.class), mock(CommandSender.class), "world",
                List.of("create", "test", "stemcraft:wasteland", "seed:-1234"));
        assertEquals("stemcraft:wasteland", WorldCommand.generatorArgument(ctx,2,"normal"));
        assertEquals("", WorldCommand.generatorArgument(ctx,3,""));
        assertEquals("-1234",ctx.getOption("seed",""));
        ctx = new CommandContextImpl(mock(Command.class), mock(CommandSender.class), "world",
                List.of("create","ocean","water","80:48","seed:42"));
        assertEquals("80:48",WorldCommand.generatorArgument(ctx,3,""));
        ctx = new CommandContextImpl(mock(Command.class), mock(CommandSender.class), "world",
                List.of("generator","info","stemcraft:deep"));
        assertEquals("stemcraft:deep",WorldCommand.generatorArgument(ctx,2,""));
    }
    @Test void defaultGenerationWithSeedKeepsItsDefault() {
        var ctx = new CommandContextImpl(mock(Command.class), mock(CommandSender.class), "world",
                List.of("create","test","seed:42"));
        assertEquals("normal",WorldCommand.generatorArgument(ctx,2,"normal"));
    }
}
