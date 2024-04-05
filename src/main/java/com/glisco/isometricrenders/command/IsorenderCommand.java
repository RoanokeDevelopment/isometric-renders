package com.glisco.isometricrenders.command;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.command.argument.PokemonPropertiesArgumentType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.glisco.isometricrenders.IsometricRenders;
import com.glisco.isometricrenders.mixin.access.BlockStateArgumentAccessor;
import com.glisco.isometricrenders.mixin.access.DefaultPosArgumentAccessor;
import com.glisco.isometricrenders.property.GlobalProperties;
import com.glisco.isometricrenders.render.*;
import com.glisco.isometricrenders.screen.RenderScreen;
import com.glisco.isometricrenders.screen.ScreenScheduler;
import com.glisco.isometricrenders.util.AreaSelectionHelper;
import com.glisco.isometricrenders.util.Translate;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.wispforest.owo.ui.component.EntityComponent;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.*;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.Util;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class IsorenderCommand {

    private static final SuggestionProvider<FabricClientCommandSource> CLIENT_SUMMONABLE_ENTITIES = (context, builder) -> CommandSource.suggestFromIdentifier(Registries.ENTITY_TYPE.stream().filter(EntityType::isSummonable),
            builder, EntityType::getId, entityType -> Text.translatable(Util.createTranslationKey("entity", EntityType.getId(entityType)))
    );

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess access) {
        dispatcher.register(literal("isorender")
                .executes(IsorenderCommand::showRootNodeHelp)
                .then(literal("area")
                        .executes(IsorenderCommand::renderAreaSelection)
                        .then(argument("start", BlockPosArgumentType.blockPos())
                                .then(argument("end", BlockPosArgumentType.blockPos())
                                        .executes(IsorenderCommand::renderAreaWithArguments))))
                .then(literal("block")
                        .executes(IsorenderCommand::renderTargetedBlock)
                        .then(argument("block", BlockStateArgumentType.blockState(access))
                                .executes(IsorenderCommand::renderBlockWithArgument)))
                .then(literal("entity")
                        .executes(IsorenderCommand::renderTargetedEntity)
                        .then(argument("entity", RegistryEntryArgumentType.registryEntry(access, RegistryKeys.ENTITY_TYPE))
                                .suggests(CLIENT_SUMMONABLE_ENTITIES)
                                .executes(IsorenderCommand::renderEntityWithoutNbt)
                                .then(argument("nbt", NbtCompoundArgumentType.nbtCompound())
                                        .executes(IsorenderCommand::renderEntityWithNbt))))
                .then(literal("pokemon")
                        .then(argument("properties", PokemonPropertiesArgumentType.Companion.properties())
                                .executes(IsorenderCommand::renderPokemon)))
                .then(literal("batch_pokemon")
                        .executes(IsorenderCommand::batchRenderPokemon)
                        .then(argument("pokemon_list", StringArgumentType.string())
                                .suggests(IsorenderCommand::suggestPokemonList)
                                .executes(IsorenderCommand::batchRenderPokemonList)))
                .then(literal("player")
                        .executes(IsorenderCommand::renderSelf)
                        .then(argument("player", StringArgumentType.string())
                                .executes(IsorenderCommand::renderPlayerWithoutNbt)
                                .then(argument("nbt", NbtCompoundArgumentType.nbtCompound())
                                        .executes(IsorenderCommand::renderPlayerWithNbt))))
                .then(literal("item")
                        .executes(IsorenderCommand::renderHeldItem)
                        .then(argument("item", ItemStackArgumentType.itemStack(access))
                                .executes(IsorenderCommand::renderItemWithArgument)))
                .then(literal("tooltip")
                        .executes(IsorenderCommand::renderHeldItemTooltip)
                        .then(argument("item", ItemStackArgumentType.itemStack(access))
                                .executes(IsorenderCommand::renderItemTooltipWithArgument)))
                .then(literal("namespace")
                        .then(argument("namespace", NamespaceArgumentType.namespace())
                                .then(argument("task", new RenderTaskArgumentType())
                                        .executes(IsorenderCommand::renderNamespace))))
                .then(literal("creative_tab")
                        .then(argument("itemgroup", ItemGroupArgumentType.itemGroup())
                                .then(argument("task", new RenderTaskArgumentType())
                                        .executes(IsorenderCommand::renderCreativeTab))))
                .then(literal("tag")
                        .then(argument("tag", new TagArgumentType(access))
                                .then(argument("task", new RenderTaskArgumentType())
                                        .executes(IsorenderCommand::renderTagContents))))
                .then(literal("unsafe")
                        .then(literal("enable")
                                .executes(IsorenderCommand::enableUnsafe))
                        .then(literal("disable")
                                .executes(IsorenderCommand::disableUnsafe))));
    }

    private static CompletableFuture<Suggestions> suggestPokemonList(CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
        Path schematicsPath = FabricLoader.getInstance().getConfigDir().resolve("skin_render/jobs");
        IsometricRenders.LOGGER.info("Suggesting files in path: " + schematicsPath.toString());

        File directory = schematicsPath.toFile();
        Stream<String> fileNamesStream = Stream.empty();
        if (directory.exists() && directory.isDirectory()) {
            fileNamesStream = Stream.of(directory.listFiles())
                    .filter(File::isFile)
                    .map(File::getName);
        } else {
            return CommandSource.suggestMatching(new String[]{"NO_FILES"}, builder);
        }

        return CommandSource.suggestMatching(fileNamesStream, builder);
    }

    private static int showRootNodeHelp(CommandContext<FabricClientCommandSource> context) {
        final var source = context.getSource();

        source.sendFeedback(Translate.prefixed(Translate.make("version", Text.literal(IsometricRenders.VERSION).formatted(Formatting.DARK_GRAY)).formatted(Formatting.GRAY)));
        source.sendFeedback(Translate.prefixed(Translate.make("command_hint").styled(
                style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://docs.wispforest.io/isometric-renders/slash_isorender/"))
                        .withFormatting(Formatting.UNDERLINE)
                        .withFormatting(Formatting.GRAY)
        )));
        return 0;
    }

    private static int disableUnsafe(CommandContext<FabricClientCommandSource> context) {
        GlobalProperties.unsafe.set(false);
        Translate.commandFeedback(context, "unsafe_disabled");
        return 0;
    }

    private static int enableUnsafe(CommandContext<FabricClientCommandSource> context) {
        GlobalProperties.unsafe.set(true);
        Translate.commandFeedback(context, "unsafe_enabled");
        return 0;
    }

    private static int renderPlayerWithNbt(CommandContext<FabricClientCommandSource> context) {
        final var server = MinecraftClient.getInstance().getServer();
        if (server == null) {
            Translate.commandError(context, "player_rendering_in_multiplayer");
            return 0;
        }

        final var gameProfile = server.getUserCache().findByName(StringArgumentType.getString(context, "player"));
        if (gameProfile.isEmpty()) {
            Translate.commandError(context, "no_such_player");
            return 0;
        }

        final var playerNbt = NbtCompoundArgumentType.getNbtCompound(context, "nbt");
        final var player = EntityComponent.createRenderablePlayer(gameProfile.get());
        ((ClientPlayerEntity) player).readNbt(
                playerNbt
        );

        ScreenScheduler.schedule(new RenderScreen(
                new EntityRenderable(player)
        ));

        return 0;
    }

    private static int renderPlayerWithoutNbt(CommandContext<FabricClientCommandSource> context) {
        final var server = MinecraftClient.getInstance().getServer();
        if (server == null) {
            Translate.commandError(context, "player_rendering_in_multiplayer");
            return 0;
        }

        final var gameProfile = server.getUserCache().findByName(StringArgumentType.getString(context, "player"));
        if (gameProfile.isEmpty()) {
            Translate.commandError(context, "no_such_player");
            return 0;
        }

        ScreenScheduler.schedule(new RenderScreen(
                new EntityRenderable(EntityComponent.createRenderablePlayer(gameProfile.get()))
        ));

        return 0;
    }

    private static int renderSelf(CommandContext<FabricClientCommandSource> context) {
        final var player = EntityComponent.createRenderablePlayer(MinecraftClient.getInstance().player.getGameProfile());
        ((ClientPlayerEntity) player).readNbt(
                MinecraftClient.getInstance().player.writeNbt(new NbtCompound())
        );

        ScreenScheduler.schedule(new RenderScreen(
                new EntityRenderable(player)
        ));
        return 0;
    }

    private static int renderTagContents(CommandContext<FabricClientCommandSource> context) {
        final var tag = TagArgumentType.getTag("tag", context);
        RenderTaskArgumentType.getTask("task", context).action.accept(
                "tag_" + tag.id().getNamespace() + "/" + tag.id().getPath(),
                tag.entries().stream()
                        .map(RegistryEntry::value)
                        .map(Item::getDefaultStack)
                        .toList()
        );
        return 0;
    }

    private static int renderCreativeTab(CommandContext<FabricClientCommandSource> context) {
        final var task = RenderTaskArgumentType.getTask("task", context);
        withItemGroupFromContext(context, (itemStacks, name) -> {
            task.action.accept(name, itemStacks);
        });
        return 0;
    }

    private static int renderNamespace(CommandContext<FabricClientCommandSource> context) {
        final var namespace = NamespaceArgumentType.getNamespace("namespace", context);
        RenderTaskArgumentType.getTask("task", context).action.accept("namespace_" + namespace.name(), namespace.getContent());
        return 0;
    }

    private static int renderItemWithArgument(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        ScreenScheduler.schedule(new RenderScreen(
                new ItemRenderable(ItemStackArgumentType.getItemStackArgument(context, "item").createStack(1, false))
        ));
        return 0;
    }

    private static int renderHeldItem(CommandContext<FabricClientCommandSource> context) {
        ScreenScheduler.schedule(new RenderScreen(
                new ItemRenderable(MinecraftClient.getInstance().player.getMainHandStack())
        ));
        return 0;
    }

    private static int renderItemTooltipWithArgument(CommandContext<FabricClientCommandSource> context) throws CommandSyntaxException {
        ScreenScheduler.schedule(new RenderScreen(
                new TooltipRenderable(ItemStackArgumentType.getItemStackArgument(context, "item").createStack(1, false))
        ));
        return 0;
    }

    private static int renderHeldItemTooltip(CommandContext<FabricClientCommandSource> context) {
        ScreenScheduler.schedule(new RenderScreen(
                new TooltipRenderable(MinecraftClient.getInstance().player.getMainHandStack())
        ));
        return 0;
    }

    @SuppressWarnings("unchecked")
    private static int renderEntityWithNbt(CommandContext<FabricClientCommandSource> context) {
        final var entityNbt = NbtCompoundArgumentType.getNbtCompound(context, "nbt");
        final var entityReference = (RegistryEntry.Reference<EntityType<?>>) context.getArgument("entity", RegistryEntry.Reference.class);

        ScreenScheduler.schedule(new RenderScreen(
                EntityRenderable.of(entityReference.value(), entityNbt)
        ));

        return 0;
    }

    private static int renderPokemon(CommandContext<FabricClientCommandSource> context) {
        final var client = MinecraftClient.getInstance();
        final var properties = PokemonPropertiesArgumentType.Companion.getPokemonProperties(context, "properties");
        final var pokemon = properties.createEntity(client.world);
        pokemon.refreshPositionAndAngles(client.player.getX(), client.player.getY(), client.player.getZ(), pokemon.getYaw(), pokemon.getPitch());

        //pokemon.getForm().getHitbox()

        ScreenScheduler.schedule(new RenderScreen(
                new PokemonRenderable(pokemon)
        ));

        return 0;
    }

    private static int batchRenderPokemon(CommandContext<FabricClientCommandSource> context) {
        final List<Pair<String, PokemonProperties>> propertyList = new ArrayList<>();

        for (Species species: PokemonSpecies.INSTANCE.getImplemented()) {
            propertyList.add(
                    new Pair<>(species.getNationalPokedexNumber() + "-" + species.getName(), PokemonProperties.Companion.parse(species.getName(), " ", "="))
            );
        }

        ScreenScheduler.schedule(new RenderScreen(
                new PokemonBatchRenderable<PokemonRenderable>("cobblemon", propertyList)
        ));

        return 0;
    }

    private static int batchRenderPokemonList(CommandContext<FabricClientCommandSource> context) {
        try {
            // Resolve the path to the JSON file
            File file = FabricLoader.getInstance().getConfigDir().resolve("skin_render/jobs/" + StringArgumentType.getString(context, "pokemon_list")).toFile();

            // Create a Gson instance
            Gson gson = new Gson();

            // Define the type of the content in the JSON file
            Type type = new TypeToken<HashMap<String, List<String>>>() {}.getType();

            // Read the file into a HashMap
            HashMap<String, List<String>> pokemonCategories = gson.fromJson(new FileReader(file), type);
            final List<Pair<String, PokemonProperties>> propertyList = new ArrayList<>();

            for (String aspect: pokemonCategories.keySet()) {
                for (String species: pokemonCategories.get(aspect)) {
                    propertyList.add(
                            new Pair<>(aspect + "-" + species, PokemonProperties.Companion.parse(species + " " + aspect, " ", "="))
                    );
                }
            }

            ScreenScheduler.schedule(new RenderScreen(
                    new PokemonBatchRenderable<PokemonRenderable>("cobblemon", propertyList)
            ));


        } catch (Exception e) {
            // Handle potential exceptions like file not found or JSON parsing error
            e.printStackTrace();
            return 0; // Indicate failure
        }

        return 1; // Indicate success
    }

    private static int renderEntityWithoutNbt(CommandContext<FabricClientCommandSource> context) {
        final var entityReference = (RegistryEntry.Reference<EntityType<?>>) context.getArgument("entity", RegistryEntry.Reference.class);

        ScreenScheduler.schedule(new RenderScreen(
                EntityRenderable.of(entityReference.value(), null)
        ));

        return 0;
    }

    private static int renderTargetedEntity(CommandContext<FabricClientCommandSource> context) {
        final var client = MinecraftClient.getInstance();

        if (client.crosshairTarget.getType() != HitResult.Type.ENTITY) {
            Translate.commandError(context, "no_entity");
            return 0;
        }

        final var targetEntity = ((EntityHitResult) client.crosshairTarget).getEntity();
        ScreenScheduler.schedule(new RenderScreen(
                EntityRenderable.copyOf(targetEntity)
        ));

        return 0;
    }

    private static int renderBlockWithArgument(CommandContext<FabricClientCommandSource> context) {
        final var stateArg = context.getArgument("block", BlockStateArgument.class);
        final var state = stateArg.getBlockState();
        final var data = ((BlockStateArgumentAccessor) stateArg).isometric$getData();

        ScreenScheduler.schedule(new RenderScreen(
                BlockStateRenderable.of(state, data)
        ));
        return 0;
    }

    private static int renderTargetedBlock(CommandContext<FabricClientCommandSource> context) {
        final var client = MinecraftClient.getInstance();

        if (client.crosshairTarget.getType() != HitResult.Type.BLOCK) {
            Translate.commandError(context, "no_block");
            return 0;
        }

        final var hitPos = ((BlockHitResult) client.crosshairTarget).getBlockPos();
        ScreenScheduler.schedule(new RenderScreen(
                BlockStateRenderable.copyOf(client.world, hitPos)
        ));

        return 0;
    }

    private static int renderAreaWithArguments(CommandContext<FabricClientCommandSource> context) {
        final var startArg = context.getArgument("start", DefaultPosArgument.class);
        final var endArg = context.getArgument("end", DefaultPosArgument.class);

        final var pos1 = getPosFromArgument(startArg, context.getSource());
        final var pos2 = getPosFromArgument(endArg, context.getSource());

        ScreenScheduler.schedule(new RenderScreen(
                AreaRenderable.of(pos1, pos2)
        ));

        return 0;
    }

    private static int renderAreaSelection(CommandContext<FabricClientCommandSource> context) {
        if (!AreaSelectionHelper.tryOpenScreen()) {
            Translate.commandError(context, "incomplete_selection");
        }

        return 0;
    }

    private static <S> void withItemGroupFromContext(CommandContext<S> context, BiConsumer<List<ItemStack>, String> action) {
        final var itemGroup = ItemGroupArgumentType.getItemGroup("itemgroup", context);
        final var stacks = new ArrayList<>(itemGroup.getDisplayStacks());
        action.accept(stacks, "creative-tab_" + Registries.ITEM_GROUP.getId(itemGroup).toShortTranslationKey());
    }

    public static BlockPos getPosFromArgument(DefaultPosArgument argument, FabricClientCommandSource source) {

        DefaultPosArgumentAccessor accessor = (DefaultPosArgumentAccessor) argument;
        Vec3d pos = source.getPlayer().getPos();

        return BlockPos.ofFloored(accessor.isometric$getX().toAbsoluteCoordinate(pos.x), accessor.isometric$getY().toAbsoluteCoordinate(pos.y), accessor.isometric$getZ().toAbsoluteCoordinate(pos.z));
    }
}
