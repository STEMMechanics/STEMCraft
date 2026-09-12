package dev.stemcraft.feature.stembot;

import dev.stemcraft.api.STEMCraftAPI;
import dev.stemcraft.api.config.ConfigFile;
import org.bukkit.plugin.Plugin;
import org.json.simple.JSONObject;

import javax.imageio.ImageIO;
import java.io.*;
import java.net.*;
import dev.stemcraft.integration.SkinRequests;
import java.util.function.Consumer;

import static dev.stemcraft.integration.CitizensAccess.invokeStatic;

/**
 * Resolves STEMBot's signed skin.
 *
 * Priority:
 * 1. pre-signed texture embedded in stembot.yml
 * 2. legacy cache migration into stembot.yml
 * 3. PNG download + Citizens/MineSkin conversion
 */
public final class BotSkinCache {
    public record Skin(String signature,String value) {}

    private long epoch;
    private Skin current;
    private final SkinRequests<Skin> requests = new SkinRequests<>();

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

        String key=script.skinUrl()+"|"+script.slim();
        File legacyFile=new File(api.getDataFolder(),"stembot-skin-cache.yml");
        ConfigFile cache=legacyFile.isFile()
            ?api.config().load("stembot-skin-cache.yml",false):null;

        if(cache!=null&&key.equals(cache.getString("source",""))
            &&!cache.getString("value","").isBlank()
            &&!cache.getString("signature","").isBlank()) {
            current=new Skin(
                cache.getString("signature"),
                cache.getString("value")
            );
            if(saveSkin(api,script,current)&&!legacyFile.delete())
                plugin.getLogger().warning("Skin migrated, but could not remove stembot-skin-cache.yml");
            ready.accept(current);
            return;
        }

        requests.request(api, plugin, "stembot.yml", key, () -> convert(script), skin -> {
            if (request != epoch || !plugin.isEnabled()) return;
            current = skin;
            try {
                saveSkin(api, script, skin);
            } finally {
                ready.accept(skin);
            }
        });
    }

    private static boolean saveSkin(STEMCraftAPI api,BotScript script,Skin skin) {
        ConfigFile config=api.config().load(new File(api.getDataFolder(),"stembot.yml"),false);
        if(config==null||!config.reload())
            throw new IllegalStateException("Unable to reload stembot.yml to save the skin");

        // Conversion is asynchronous: preserve edits made while it was running.
        if(!config.getString("skin.url","").equals(script.skinUrl())
            ||config.getBoolean("skin.slim",false)!=script.slim()
            ||!config.getString("skin.texture.value","").isBlank()
            ||!config.getString("skin.texture.signature","").isBlank())
            return false;

        config.set("skin.texture.value",skin.value());
        config.set("skin.texture.signature",skin.signature());
        config.save();
        return !config.isDirty();
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
