package dev.stemcraft.feature.stembot;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import org.bukkit.plugin.Plugin;
import org.json.simple.JSONObject;

import javax.imageio.ImageIO;
import java.io.*;
import java.net.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static dev.stemcraft.integration.CitizensAccess.invokeStatic;

/**
 * Resolves STEMBot's signed skin.
 *
 * Priority:
 * 1. pre-signed texture embedded in stembot.yml
 * 2. persistent generated cache
 * 3. PNG download + Citizens/MineSkin conversion
 */
public final class BotSkinCache {
    public record Skin(String signature,String value) {}

    private long epoch;
    private Skin current;

    public Skin current() {
        return current;
    }

    public void close() {
        epoch++;
        current=null;
    }

    public void load(
        STEMCraftAPI api,
        Plugin plugin,
        BotScript script,
        Consumer<Skin> ready
    ) {
        long request=++epoch;
        current=null;

        if(!script.skinValue().isBlank()&&!script.skinSignature().isBlank()) {
            current=new Skin(script.skinSignature(),script.skinValue());
            ready.accept(current);
            return;
        }

        if(script.skinUrl().isBlank()) return;

        ConfigFile cache=api.config().load("stembot-skin-cache.yml");
        String key=script.skinUrl()+"|"+script.slim();

        assert cache != null;
        if(key.equals(cache.getString("source",""))
            &&!cache.getString("value","").isBlank()
            &&!cache.getString("signature","").isBlank()) {
            current=new Skin(
                cache.getString("signature"),
                cache.getString("value")
            );
            ready.accept(current);
            return;
        }

        CompletableFuture.supplyAsync(()->convert(script))
            .whenComplete((skin,error)->api.tasks().nextTick(()->{
                if(request!=epoch||!plugin.isEnabled()) return;

                if(error!=null) {
                    plugin.getLogger().log(
                        java.util.logging.Level.WARNING,
                        "STEMBot skin could not be loaded; using the default skin",
                        error
                    );
                    return;
                }

                current=skin;
                cache.set("source",key);
                cache.set("signature",skin.signature());
                cache.set("value",skin.value());
                cache.save();
                ready.accept(skin);
            }));
    }

    private static Skin convert(BotScript script) {
        try {
            URI uri=URI.create(script.skinUrl());
            if(!uri.getScheme().equals("http")&&!uri.getScheme().equals("https"))
                throw new IOException("Skin URL must use HTTP or HTTPS");

            URLConnection connection=uri.toURL().openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);

            byte[] png;
            try(InputStream in=connection.getInputStream()) {
                png=in.readNBytes(1_048_577);
            }

            if(png.length>1_048_576)
                throw new IOException("Skin exceeds 1 MiB");

            var image=ImageIO.read(new ByteArrayInputStream(png));
            if(image==null
                ||image.getWidth()!=64
                ||(image.getHeight()!=64&&image.getHeight()!=32))
                throw new IOException("Skin must be a 64x64 or 64x32 PNG");

            JSONObject result=(JSONObject)invokeStatic(
                "net.citizensnpcs.util.MojangSkinGenerator",
                "generateFromPNG",
                png,
                script.slim()
            );

            if(result==null||!(result.get("texture") instanceof JSONObject texture))
                throw new IOException("Skin service returned no texture");

            Object signature=texture.get("signature");
            Object value=texture.get("value");

            if(!(signature instanceof String signed)
                ||!(value instanceof String encoded)
                ||signed.isBlank()
                ||encoded.isBlank())
                throw new IOException("Skin service returned an unsigned texture");

            return new Skin(signed,encoded);
        } catch(Exception error) {
            throw new IllegalStateException("Unable to prepare STEMBot skin",error);
        }
    }
}
