package fr.iglee42.placeableingots.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ClientConfig {

    private static File configFile;

    public static Map<String, Integer> colorOverrides = new HashMap<>();




    public static void load() throws IOException, IllegalAccessException {
        configFile = new File(FMLPaths.CONFIGDIR.get().toFile(),"placeableingots-client.json");
        if (configFile.exists()){
            JsonObject config = new Gson().fromJson(new FileReader(configFile),JsonObject.class);
            JsonObject overrideObj = config.getAsJsonObject("colorOverrides");
            overrideObj.entrySet().forEach(entry->{
                colorOverrides.put(entry.getKey(),Integer.parseInt(entry.getValue().getAsString(),16));
            });
        } else {
            JsonObject config = new JsonObject();
            config.add("colorOverrides",new JsonObject());
            FileWriter writer = new FileWriter(configFile);
            writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(config));
            writer.close();
        }
    }
}
