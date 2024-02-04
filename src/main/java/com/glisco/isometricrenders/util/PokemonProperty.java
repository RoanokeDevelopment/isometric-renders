package com.glisco.isometricrenders.util;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.glisco.isometricrenders.IsometricRenders;
import com.glisco.isometricrenders.property.DefaultPropertyBundle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class PokemonProperty extends DefaultPropertyBundle {

    public String species;

    public PokemonProperty(String species) {
        super();
        this.species = species;
    }

    public File getSaveFile() {
        File
        File save = PokemonPropertyManager.propertyDir.resolve(species + ".json").toFile();
        try {
            save.createNewFile();
        } catch (Exception e) {
            IsometricRenders.LOGGER.error("Failed to create save file for Pokemon Properties");
        }
        return save;
    }

    public void save() {
        Map<String, Object> propertiesMap = new HashMap<>();
        propertiesMap.put("species", species);
        propertiesMap.put("scale", scale.get());
        propertiesMap.put("rotation", rotation.get());
        propertiesMap.put("slant", slant.get());
        propertiesMap.put("lightAngle", lightAngle.get());
        propertiesMap.put("xOffset", xOffset.get());
        propertiesMap.put("yOffset", yOffset.get());

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(propertiesMap);

        File saveFile = getSaveFile();
        try (FileWriter writer = new FileWriter(saveFile)) {
            writer.write(json);
        } catch (IOException e) {
            IsometricRenders.LOGGER.error("Failed to save Pokemon Properties", e);
        }
    }

    public static PokemonProperty fromFile(File file) {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> propertiesMap = gson.fromJson(reader, type);

            String species = (String) propertiesMap.get("species");
            PokemonProperty pokemonProperty = new PokemonProperty(species);

            if (propertiesMap.containsKey("scale")) {
                pokemonProperty.scale.set(((Number) propertiesMap.get("scale")).intValue());
            }
            if (propertiesMap.containsKey("rotation")) {
                pokemonProperty.rotation.set(((Number) propertiesMap.get("rotation")).intValue());
            }
            if (propertiesMap.containsKey("slant")) {
                pokemonProperty.slant.set(((Number) propertiesMap.get("slant")).intValue());
            }
            if (propertiesMap.containsKey("lightAngle")) {
                pokemonProperty.lightAngle.set(((Number) propertiesMap.get("lightAngle")).intValue());
            }
            if (propertiesMap.containsKey("xOffset")) {
                pokemonProperty.xOffset.set(((Number) propertiesMap.get("xOffset")).intValue());
            }
            if (propertiesMap.containsKey("yOffset")) {
                pokemonProperty.yOffset.set(((Number) propertiesMap.get("yOffset")).intValue());
            }

            return pokemonProperty;
        } catch (IOException e) {
            IsometricRenders.LOGGER.error("Failed to load Pokemon Properties from file", e);
            return null; // or handle it in another way
        }
    }

}
