package com.glisco.isometricrenders.render;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.glisco.isometricrenders.property.DefaultPropertyBundle;
import com.glisco.isometricrenders.util.ExportPathSpec;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;

public class PokemonRenderable extends EntityRenderable implements TickingRenderable<DefaultPropertyBundle> {

    public final PokemonEntity pokemonEntity;

    public PokemonRenderable(PokemonEntity entity) {
        super(entity);
        this.pokemonEntity = entity;
    }
/*
    @Override
    public void prepare() {
        // do nothing
    }

    @Override
    public void cleanUp() {
        // do nothing
    }
*/
    @Override
    public ExportPathSpec exportPath() {
        return ExportPathSpec.of("cobblemon", String.valueOf(this.pokemonEntity.getPokemon().getSpecies().getNationalPokedexNumber()));
    }

}
