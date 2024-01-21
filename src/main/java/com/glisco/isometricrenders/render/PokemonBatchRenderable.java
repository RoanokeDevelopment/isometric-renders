package com.glisco.isometricrenders.render;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.glisco.isometricrenders.property.DefaultPropertyBundle;
import com.glisco.isometricrenders.property.GlobalProperties;
import com.glisco.isometricrenders.property.PropertyBundle;
import com.glisco.isometricrenders.screen.IsometricUI;
import com.glisco.isometricrenders.util.ExportPathSpec;
import com.glisco.isometricrenders.util.ImageIO;
import com.glisco.isometricrenders.util.Translate;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

import java.util.List;

public class PokemonBatchRenderable<R extends Renderable<?>> implements Renderable<PokemonBatchRenderable.PokemonBatchPropertyBundle> {

    private final PokemonBatchPropertyBundle properties;
    private final List<PokemonProperties> pokemonList;
    private final String contentType;
    public boolean renderingShadows = true;

    private PokemonRenderable currentDelegate;
    private int currentIndex;

    private long renderDelay;
    private long lastRenderTime;

    private boolean batchActive;

    public PokemonBatchRenderable(String source, List<PokemonProperties> delegates) {
        this.pokemonList = delegates;
        this.reset();

        this.contentType = ExportPathSpec.exportRoot().resolve("batches/")
                .relativize(ImageIO.next(ExportPathSpec.exportRoot().resolve("batches/" + source + "/"))).toString();

        this.properties = new PokemonBatchPropertyBundle(this.currentDelegate.properties());
        this.renderDelay = Math.max((int) Math.pow(GlobalProperties.exportResolution / 1024f, 2) * 100L, 75);
    }

    public static <R extends Renderable<?>> PokemonBatchRenderable<?> of(String source, List<PokemonProperties> delegates) {
        if (delegates.isEmpty()) {
            return new PokemonBatchRenderable<>(source, List.of());
        } else {
            return new PokemonBatchRenderable<>(source, delegates);
        }
    }

    @Override
    public void emitVertices(MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta) {
        this.currentDelegate.emitVertices(matrices, vertexConsumers, tickDelta);

        if (this.batchActive && this.currentIndex < this.pokemonList.size() && System.currentTimeMillis() - this.lastRenderTime > this.renderDelay && ImageIO.taskCount() <= 5) {
            //final var image = RenderableDispatcher.drawIntoImage(this.currentDelegate, 0, GlobalProperties.exportResolution);
            //ImageIO.save(image, this.exportPath());

            ImageIO.save(
                    RenderableDispatcher.copyFramebufferIntoImage(RenderableDispatcher.drawIntoTexture(this.currentDelegate, tickDelta, GlobalProperties.exportResolution)),
                    this.currentDelegate.exportPath());


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
        if (this.currentIndex < this.pokemonList.size()) {
            final var client = MinecraftClient.getInstance();
            final var properties = this.pokemonList.get(currentIndex);
            final var pokemon = properties.createEntity(client.world);
            pokemon.refreshPositionAndAngles(client.player.getX(), client.player.getY(), client.player.getZ(), pokemon.getYaw(), pokemon.getPitch());
            this.currentDelegate = new PokemonRenderable(pokemon);
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

    public static class PokemonBatchPropertyBundle extends DefaultPropertyBundle {

        private final PropertyBundle delegate;

        public PokemonBatchPropertyBundle(PropertyBundle delegate) {
            this.delegate = delegate;

            // A bit ugly, but we copy all property values from the delegate and hook
            // the delegate onto our properties - this makes sure we don't always reset
            // the properties and that the mouse and keyboard controls actually affect the delegate
            if (this.delegate instanceof DefaultPropertyBundle defaultPropertyBundle) {
                this.scale.copyFrom(defaultPropertyBundle.scale);
                this.rotation.copyFrom(defaultPropertyBundle.rotation);
                this.slant.copyFrom(defaultPropertyBundle.slant);
                this.lightAngle.copyFrom(defaultPropertyBundle.lightAngle);
                this.xOffset.copyFrom(defaultPropertyBundle.xOffset);
                this.yOffset.copyFrom(defaultPropertyBundle.yOffset);

                this.scale.listen(defaultPropertyBundle.scale);
                this.rotation.listen(defaultPropertyBundle.rotation);
                this.slant.listen(defaultPropertyBundle.slant);
                this.lightAngle.listen(defaultPropertyBundle.lightAngle);
                this.xOffset.listen(defaultPropertyBundle.xOffset);
                this.yOffset.listen(defaultPropertyBundle.yOffset);
            }
        }

        @Override
        public void buildGuiControls(Renderable<?> renderable, FlowLayout container) {
            final PokemonBatchRenderable<?> batchRenderable = (PokemonBatchRenderable<?>) renderable;

            this.delegate.buildGuiControls(batchRenderable.currentDelegate, container);

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
            }

            IsometricUI.dynamicLabel(container, () -> Translate.gui(
                    "batch.remaining",
                    Math.max(0, batchRenderable.pokemonList.size() - batchRenderable.currentIndex - 1),
                    batchRenderable.pokemonList.size()
            ));
        }

        @Override
        public void applyToViewMatrix(MatrixStack modelViewStack) {
            this.delegate.applyToViewMatrix(modelViewStack);
        }

    }
}
