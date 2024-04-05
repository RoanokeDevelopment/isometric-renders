package com.glisco.isometricrenders.util;


import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Species;

public class PokemonRenderJob {
    public String properties;
    public String folder;
    public String filename;
    public String species;

    // No-argument constructor
    public PokemonRenderJob() {
    }

    // Your existing constructor can stay if needed for other purposes
    public PokemonRenderJob(String folder, String filename, String species, String properties) {
        this.folder = folder;
        this.properties = properties;
        this.filename = filename;
        this.species = species;
    }

    // Assuming PokemonProperties.Companion.parse is correctly implemented
    public PokemonProperties getProperties() {
        return PokemonProperties.Companion.parse(this.properties, " ", "=");
    }

    public static PokemonRenderJob fromSpecies(Species species) {
        return new PokemonRenderJob("cobblemon", species.getNationalPokedexNumber() + species.getName(), species.getName(), species.getName());
    }

}