package com.glisco.isometricrenders.util;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.glisco.isometricrenders.IsometricRenders;
import com.glisco.isometricrenders.property.DefaultPropertyBundle;
import com.glisco.isometricrenders.property.IntProperty;
import com.glisco.isometricrenders.render.EntityRenderable;
import com.glisco.isometricrenders.render.Renderable;
import com.glisco.isometricrenders.screen.IsometricUI;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class PokemonProperty extends EntityRenderable.EntityPropertyBundle {

    public String species;

    public PokemonProperty(String species) {
        super();
        this.species = species;
        if (fileExists()) {
            loadFile(getSaveFile());
        } else {
            IsometricRenders.LOGGER.info("New Pokemon rendering time!!!");
        }
    }
/*
    @Override
    public void buildGuiControls(Renderable<?> renderable, FlowLayout container) {
        IsometricUI.sectionHeader(container, "transform_options", false);

        IsometricUI.intControl(container, this.scale, "scale", 10);
        IsometricUI.intControl(container, this.rotation, "rotation", 45);
        IsometricUI.intControl(container, this.slant, "slant", 30);
        IsometricUI.intControl(container, this.lightAngle, "light_angle", 15);
        IsometricUI.intControl(container, this.rotationSpeed, "rotation_speed", 5);

        IsometricUI.sectionHeader(container, "entity_pose", true);

        IsometricUI.intControl(container, this.yaw, "entity_pose.yaw", 15);
        IsometricUI.intControl(container, this.pitch, "entity_pose.pitch", 5);

        // -------

        IsometricUI.sectionHeader(container, "presets", true);

        try (var builder = IsometricUI.row(container)) {
            builder.row.child(Components.button(Translate.gui("dimetric"), (ButtonComponent button) -> {
                this.rotation.setToDefault();
                this.slant.set(30);
            }).horizontalSizing(Sizing.fixed(60)).margins(Insets.right(5)));

            builder.row.child(Components.button(Translate.gui("isometric"), (ButtonComponent button) -> {
                this.rotation.setToDefault();
                this.slant.set(36);
            }).horizontalSizing(Sizing.fixed(60)));
        }
    }
*/
    public File getSaveFile() {
        // Resolve the path to the directory where the file should be.
        File dir = FabricLoader.getInstance().getConfigDir().resolve("cobblemon-properties").toFile();

        // Ensure the directory exists.
        dir.mkdirs();

        // Now resolve the path to the file within that directory.
        File save = new File(dir, species + ".json");

        // Attempt to create the file if it doesn't exist.
        try {
            save.createNewFile();
        } catch (Exception e) {
            IsometricRenders.LOGGER.error("Failed to create save file for Pokemon Properties", e);
        }
        return save;
    }

    public boolean fileExists() {
        File dir = FabricLoader.getInstance().getConfigDir().resolve("cobblemon-properties").toFile();

        // Ensure the directory exists.
        dir.mkdirs();

        // Now resolve the path to the file within that directory.
        File save = new File(dir, species + ".json");
        return save.exists();
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

    public void loadFile(File file) {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> propertiesMap = gson.fromJson(reader, type);

            String species = (String) propertiesMap.get("species");

            if (propertiesMap.containsKey("scale")) {
                this.scale.set(((Number) propertiesMap.get("scale")).intValue());
            }
            if (propertiesMap.containsKey("rotation")) {
                this.rotation.set(((Number) propertiesMap.get("rotation")).intValue());
            }
            if (propertiesMap.containsKey("slant")) {
                this.slant.set(((Number) propertiesMap.get("slant")).intValue());
            }
            if (propertiesMap.containsKey("lightAngle")) {
                this.lightAngle.set(((Number) propertiesMap.get("lightAngle")).intValue());
            }
            if (propertiesMap.containsKey("xOffset")) {
                this.xOffset.set(((Number) propertiesMap.get("xOffset")).intValue());
            }
            if (propertiesMap.containsKey("yOffset")) {
                this.yOffset.set(((Number) propertiesMap.get("yOffset")).intValue());
            }

        } catch (IOException e) {
            IsometricRenders.LOGGER.error("Failed to load Pokemon Properties from file", e);
        }
    }

}
