package com.glisco.isometricrenders.render;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.glisco.isometricrenders.IsometricRenders;
import com.glisco.isometricrenders.property.GlobalProperties;
import com.glisco.isometricrenders.screen.IsometricUI;
import com.glisco.isometricrenders.util.*;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Pair;
import org.joml.Matrix4f;

import java.util.List;

public class PokemonBatchRenderable<R extends Renderable<?>> implements Renderable<PokemonProperty> {

    private PokemonBatchPropertyBundle properties;
    private final List<PokemonRenderJob> jobList;
    private final String contentType;

    private PokemonRenderable currentDelegate;
    private int currentIndex;

    private long renderDelay;
    private long lastRenderTime;

    private boolean batchActive;

    public PokemonBatchRenderable(List<PokemonRenderJob> jobList) {
        this.jobList = jobList;
        this.reset();

        this.contentType = ExportPathSpec.exportRoot().resolve("batches/")
                .relativize(ImageIO.next(ExportPathSpec.exportRoot().resolve("batches/"))).toString();

        this.properties = new PokemonBatchPropertyBundle(this.currentDelegate.pokemonEntity.getPokemon().getSpecies().getName());
        this.renderDelay = Math.max((int) Math.pow(GlobalProperties.exportResolution / 1024f, 2) * 100L, 75);
    }

    public static <R extends Renderable<?>> PokemonBatchRenderable<?> of(List<PokemonRenderJob> jobList) {
        if (jobList.isEmpty()) {
            return new PokemonBatchRenderable<>(List.of());
        } else {
            return new PokemonBatchRenderable<>(jobList);
        }
    }

    @Override
    public void emitVertices(MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta) {
        this.currentDelegate.emitVertices(matrices, vertexConsumers, tickDelta);

        if (this.batchActive && this.currentIndex < this.jobList.size() && System.currentTimeMillis() - this.lastRenderTime > this.renderDelay && ImageIO.taskCount() <= 5) {
            //final var image = RenderableDispatcher.drawIntoImage(this.currentDelegate, 0, GlobalProperties.exportResolution);
            //ImageIO.save(image, this.exportPath());
            this.properties = new PokemonBatchPropertyBundle(this.currentDelegate.pokemonEntity.getPokemon().getSpecies().getName());
            ImageIO.save(
                    RenderableDispatcher.copyFramebufferIntoImage(RenderableDispatcher.drawIntoTexture(this.currentDelegate, tickDelta, GlobalProperties.exportResolution)),
                    ExportPathSpec.of("cobblemon", this.jobList.get(currentIndex).filename));


            this.getNextDelegate();
            this.lastRenderTime = System.currentTimeMillis();
        }
    }

    @Override
    public void draw(Matrix4f modelViewMatrix) {
        this.currentDelegate.draw(modelViewMatrix);
    }

    private void start() {
        this.batchActive = true;
        this.currentIndex = 0;
        this.lastRenderTime = System.currentTimeMillis();
        this.renderDelay = Math.max((int) Math.pow(GlobalProperties.exportResolution / 1024f, 2) * 100L, 75);
    }

    private void reset() {
        this.batchActive = false;
        this.lastRenderTime = -1;
        this.currentIndex = -1;
        getNextDelegate();
    }

    private void getNextDelegate() {
        this.currentIndex++;
        if (this.currentIndex < this.jobList.size()) {
            final var client = MinecraftClient.getInstance();
            final var properties = this.jobList.get(currentIndex).getProperties();
            final var pokemon = properties.createEntity(client.world);
            pokemon.refreshPositionAndAngles(client.player.getX(), client.player.getY(), client.player.getZ(), pokemon.getYaw(), pokemon.getPitch());
            this.currentDelegate = new PokemonRenderable(pokemon);
            this.properties = new PokemonBatchPropertyBundle(this.currentDelegate.pokemonEntity.getPokemon().getSpecies().getName());
            if (!this.batchActive) {
                this.properties.rebuildGui();
            }
        }
    }

    @Override
    public PokemonBatchPropertyBundle properties() {
        return this.properties;
    }

    @Override
    public ExportPathSpec exportPath() {
        return this.currentDelegate.exportPath().relocate("batches/" + this.contentType);
    }

    public static class PokemonBatchPropertyBundle extends PokemonProperty {

        public boolean shouldRebuildGui = false;

        public PokemonBatchPropertyBundle(String species) {
            super(species);
            IsometricRenders.LOGGER.info("Creating Pokemon Batch Propety with Species: "  + species);
        }

        public void rebuildGui() {
            shouldRebuildGui = true;
        }

        @Override
        public boolean shouldRebuildGui() {
            if (shouldRebuildGui) {
                shouldRebuildGui = false;
                return true;
            }
            return false;
        }

        @Override
        public void buildGuiControls(Renderable<?> renderable, FlowLayout container) {
            final PokemonBatchRenderable<?> batchRenderable = (PokemonBatchRenderable<?>) renderable;
            IsometricRenders.LOGGER.info("Creating gui controls for: " + batchRenderable.currentDelegate.pokemonEntity.getPokemon().getSpecies().getName() + " <- delegate, " + batchRenderable.properties.species + " <- properties species, " + this.species + " <- this.species");
            super.buildGuiControls(batchRenderable.currentDelegate, container);

            IsometricUI.sectionHeader(container, "batch.controls", true);
            try (var builder = IsometricUI.row(container)) {
                final var startButton = Components.button(Translate.gui("batch.start"), (ButtonComponent button) -> {
                    batchRenderable.start();
                    button.active = false;
                });
                builder.row.child(startButton.horizontalSizing(Sizing.fixed(60)).margins(Insets.right(5)));
                builder.row.child(Components.button(Translate.gui("batch.reset"), (ButtonComponent button) -> {
                    batchRenderable.reset();
                    startButton.active = true;
                }));
                builder.row.child(Components.button(Text.literal("Next"), (ButtonComponent button) -> {
                    batchRenderable.getNextDelegate();
                }));
                builder.row.child(Components.button(Text.literal("Save"), (ButtonComponent button) -> {
                    IsometricRenders.LOGGER.info("Trying to save pokemon properties... for species: " + batchRenderable.properties.species + " in delegate: " + ((PokemonBatchRenderable) renderable).currentDelegate.pokemonEntity.getPokemon().getSpecies().getName());
                    batchRenderable.properties.save();
                }));
            }

            IsometricUI.dynamicLabel(container, () -> Translate.gui(
                    "batch.remaining",
                    Math.max(0, batchRenderable.jobList.size() - batchRenderable.currentIndex - 1),
                    batchRenderable.jobList.size()
            ));
        }
    }
}
