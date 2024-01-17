package com.glisco.isometricrenders.render;

import com.glisco.isometricrenders.property.DefaultPropertyBundle;
import net.minecraft.entity.Entity;

public class PokemonRenderable extends EntityRenderable implements TickingRenderable<DefaultPropertyBundle> {
    public PokemonRenderable(Entity entity) {
        super(entity);
    }

    @Override
    public void prepare() {
        // do nothing
    }

    @Override
    public void cleanUp() {
        // do nothing
    }

}
