package me.paulf.hatstands.server;

import com.google.common.base.Splitter;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.Iterators;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import me.paulf.hatstands.util.Mth;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFaceBlock;
import net.minecraft.block.JukeboxBlock;
import net.minecraft.block.RedstoneLampBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.IDyeableArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MusicDiscItem;
import net.minecraft.network.play.server.SEntityEquipmentPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Direction;
import net.minecraft.util.HTTPUtil;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.PlaySoundAtEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;

import javax.annotation.Nullable;
import java.net.Proxy;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;

public class AskAlexaBehavior implements Behavior {
    private static final DateTimeFormatter NEWS_DATE_FORMATTER = new DateTimeFormatterBuilder()
        .appendText(MONTH_OF_YEAR)
        .appendLiteral(' ')
        .appendText(DAY_OF_MONTH)
        .toFormatter(Locale.US);

    private final HatStandEntity entity;

    private final String deliminators = "!,.;?";

    private final Splitter argSplitter = Splitter.on(Pattern.compile(" |(?=[" + this.deliminators + "])")).omitEmptyStrings();

    private final EvictingQueue<Request> requests = EvictingQueue.create(16);

    private int remaining = 0;

    AskAlexaBehavior(final HatStandEntity entity) {
        this.entity = entity;
    }

    private void enqueue(final Request request) {
        this.requests.offer(request);
    }

    @Override
    public void onStart() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onUpdate() {
        final Request pending = this.requests.peek();
        if (pending != null) {
            if (pending.time == 6) {
                final ServerChunkProvider tracker = ((ServerWorld) this.entity.world).getChunkProvider();
                final ItemStack ring = new ItemStack(Items.LEATHER_HELMET);
                ((IDyeableArmorItem) ring.getItem()).setColor(ring, "Picton BluE OBtAInEd".hashCode() & 0xFFFFFF);
                tracker.sendToAllTracking(this.entity, new SEntityEquipmentPacket(this.entity.getEntityId(), EquipmentSlotType.HEAD, ring));
                this.entity.playSound(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 0.125F, 0.668F);
                this.remaining = 32;
            }
            if (pending.time-- <= 0) {
                this.handle(pending.user, pending.args.iterator());
                this.requests.poll();
            }
        }
        if (this.remaining > 0) {
            this.remaining--;
            if (this.remaining == 0) {
                final ServerChunkProvider tracker = ((ServerWorld) this.entity.world).getChunkProvider();
                tracker.sendToAllTracking(this.entity, new SEntityEquipmentPacket(this.entity.getEntityId(), EquipmentSlotType.HEAD, this.entity.getItemStackFromSlot(EquipmentSlotType.HEAD)));
                this.entity.playSound(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 0.125F, 0.0F);
            }
        }
    }

    @Override
    public void onEnd() {
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    @SubscribeEvent
    public void onChat(final ServerChatEvent event) {
        final ServerPlayerEntity player = event.getPlayer();
        if (this.entity.getDistanceSq(player) < 10.0F * 10.0F) {
            final String msg = event.getMessage();
            char ch;
            if (msg.length() >= 6 &&
                ((ch = msg.charAt(3)) == 'X' || ch == 'x') &&
                ((ch = msg.charAt(4)) == 'A' || ch == 'a') &&
                ((ch = msg.charAt(1)) == 'L' || ch == 'l') &&
                ((ch = msg.charAt(0)) == 'A' || ch == 'a') &&
                ((ch = msg.charAt(2)) == 'E' || ch == 'e')) {
                for (int n = 5, len = msg.length(); n < len; ) {
                    ch = msg.charAt(n++);
                    if (ch == ',') {
                        this.enqueue(new Request(player.getDisplayName(), this.argSplitter.split(msg.substring(n)), 14));
                        break;
                    } else if (ch != ' ') {
                        break;
                    }
                }
            }
        }
    }

    private void handle(final ITextComponent user, final Iterator<String> args) {
        final String TROUBLE_UNDERSTANDING = "I am having trouble understanding, try again later.";
        final String NOT_SURE = "Hmm, I'm not sure.";
        final String SOMETHING_WENT_WRONG = "Hmm, Sorry, something went wrong. Please try in a little while.";
        final int RECORD_EVENT = 1010;
        final HatStandEntity e = this.entity;
        final World world = e.world;
        if (args.hasNext()) {
            final String first = args.next();
            if ("good".equals(first) && args.hasNext()) {
                final String second = args.next();
                if (this.endsWith(args, ".!") && ("morning".equals(second) || "afternoon".equals(second) || "evening".equals(second))) {
                    e.typeMessage(String.format("Good %s.", second.toLowerCase(Locale.ROOT)));
                } else {
                    e.typeMessage(TROUBLE_UNDERSTANDING);
                }
            } else if ("flip".equals(first)) {
                if (Iterators.elementsEqual(args, Iterators.forArray("a", "coin")) && this.endsWith(args, ".!")) {
                    e.typeMessage(e.getRNG().nextBoolean() ? "Heads" : "Tails");
                } else {
                    e.typeMessage(TROUBLE_UNDERSTANDING);
                }
            } else if ("play".equals(first) && args.hasNext()) {
                final String second = this.join(args, " ", ".!");
                final String name = second.replace(' ', '_').toLowerCase(Locale.ROOT);
                Item item;
                if (second.isEmpty() || args.hasNext()) {
                    e.typeMessage(TROUBLE_UNDERSTANDING);
                } else if (
                    (item = this.getValue(ForgeRegistries.ITEMS, "music_disc_" + name)) instanceof MusicDiscItem ||
                    (item = this.getValue(ForgeRegistries.ITEMS, name)) instanceof MusicDiscItem
                ) {
                    final SortedSet<BlockPos> candidates = this.findJukebox();
                    if (candidates.isEmpty()) {
                        e.typeMessage("I can't find any music in your library.");
                    } else {
                        world.playEvent(RECORD_EVENT, candidates.first(), Item.getIdFromItem(item));
                    }
                } else {
                    e.typeMessage(String.format("I'm sorry, I cannot find '%s'", second));
                }
            } else if ("stop".equals(first) && this.endsWith(args, ".!")) {
                final SortedSet<BlockPos> candidates = this.findJukebox();
                if (!candidates.isEmpty()) {
                    world.playEvent(RECORD_EVENT, candidates.first(), 0);
                }
            } else if ("turn".equals(first) && args.hasNext()) {
                final String second = args.next();
                if (args.hasNext() && ("on".equals(second) || "off".equals(second))) {
                    final String third = args.next();
                    final String thing;
                    if (args.hasNext() && "the".equals(third)) {
                        thing = args.next();
                    } else {
                        thing = third;
                    }
                    if ("lamp".equals(thing) && this.endsWith(args, ".!")) {
                        final boolean lit = !"on".equals(second);
                        final SortedSet<BlockPos> candidates = this.findBlock(state -> state.getBlock() == Blocks.REDSTONE_LAMP && state.get(RedstoneLampBlock.LIT) == lit);
                        if (candidates.isEmpty()) {
                            e.typeMessage("I can't find any lamps.");
                        } else if (this.togglePower(candidates.first(), true)) {
                            e.typeMessage("I can't power any lamps.");
                        }
                    } else {
                        e.typeMessage(TROUBLE_UNDERSTANDING);
                    }
                } else {
                    e.typeMessage(TROUBLE_UNDERSTANDING);
                }
            } else if ("will".equals(first)) {
                final String second = args.next();
                if ("it".equals(second) && args.hasNext()) {
                    final String third = args.next();
                    final String day;
                    if (("rain".equals(third) || "snow".equals(third)) && args.hasNext() && ("today".equals(day = args.next()) || "tomorrow".equals(day)) && args.hasNext() && "?".equals(args.next()) && !args.hasNext()) {
                        final BlockPos pos = new BlockPos(e);
                        final Biome biome = world.getBiome(pos);
                        final String verb, noun;
                        if (biome.getPrecipitation() != Biome.RainType.NONE) {
                            if (biome.getTemperature(world.getHeight(Heightmap.Type.MOTION_BLOCKING, pos)) >= 0.15F) {
                                verb = "raining";
                                noun = "rain";
                            } else {
                                verb = "snowing";
                                noun = "snow";
                            }
                        } else {
                            verb = "storming";
                            noun = "storm";
                        }
                        final long DAY = 24000L;
                        final WorldInfo info = world.getWorldInfo();
                        if (info.isRaining()) {
                            if ("today".equals(day)) {
                                e.typeMessage(String.format("It is %s right now.", verb));
                            } else {
                                if (world.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE)) {
                                    final long tick = info.getGameTime();
                                    final long expected = Math.max(info.getClearWeatherTime(), info.getRainTime());
                                    final long te = (tick + 6000L) % DAY + expected;
                                    if (te >= DAY) {
                                        if (te < 2 * DAY) {
                                            e.typeMessage(String.format("It is expected to %s tomorrow until %s.", noun, this.formatTime(world, tick + expected)));
                                        } else {
                                            e.typeMessage(String.format("It is expected to %s all day tomorrow.", noun));
                                        }
                                    } else {
                                        e.typeMessage(String.format("No %s is expected tomorrow.", noun));
                                    }
                                } else {
                                    e.typeMessage(String.format("It is expected to %s all day tomorrow.", noun));
                                }
                            }
                        } else if (world.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE)) {
                            final long from = "today".equals(day) ? 0 : DAY;
                            final long tick = info.getGameTime();
                            final long expected = Math.max(info.getClearWeatherTime(), info.getRainTime());
                            final long te = (tick + 6000L) % DAY + expected;
                            if (te >= from && te < from + DAY) {
                                e.typeMessage(String.format("It might %s %s at %s.", noun, day.toLowerCase(Locale.ROOT), this.formatTime(world, tick + expected)));
                            } else {
                                e.typeMessage(String.format("No %s is expected %s.", noun, day.toLowerCase(Locale.ROOT)));
                            }
                        } else {
                            e.typeMessage(String.format("No %s is expected %s.", noun, day.toLowerCase(Locale.ROOT)));
                        }
                    } else {
                        e.typeMessage(NOT_SURE);
                    }
                } else {
                    e.typeMessage(TROUBLE_UNDERSTANDING);
                }
            } else if ("who".equals(first)) {
                if (Iterators.elementsEqual(args, Iterators.forArray("am", "I", "?"))) {
                    e.typeMessage(new StringTextComponent("You are ").appendSibling(user).appendText("."));
                } else {
                    e.typeMessage(NOT_SURE);
                }
            } else if ("what".equals(first)) {
                final String second = args.next();
                if ("time".equals(second) && Iterators.elementsEqual(args, Iterators.forArray("is", "it", "?"))) {
                    e.typeMessage(String.format("It is %s.", this.formatTime(world, world.getGameTime())));
                } else if ("does".equals(second) && args.hasNext() && "the".equals(args.next()) && args.hasNext()) {
                    final String name = this.join(args, "_", "say");
                    if (!name.isEmpty() && this.endsWith(args, "?")) {
                        final EntityType<?> type = this.getValue(ForgeRegistries.ENTITIES, name);
                        if (type != null) {
                            final boolean[] sound = { false };
                            final Consumer<PlaySoundAtEntityEvent> listener = event -> sound[0] = true;
                            try {
                                MinecraftForge.EVENT_BUS.addListener(listener);
                                final Entity dummy = type.create(world, null, null, null, new BlockPos(this.entity), SpawnReason.MOB_SUMMONED, false, false);
                                if (dummy != null) dummy.remove();
                            } finally {
                                MinecraftForge.EVENT_BUS.unregister(listener);
                            }
                            if (!sound[0]) {
                                e.typeMessage(NOT_SURE);
                            }
                        } else {
                            e.typeMessage(NOT_SURE);
                        }
                    } else {
                        e.typeMessage(NOT_SURE);
                    }
                } else {
                    e.typeMessage(NOT_SURE);
                }
            } else if ("what's".equals(first) && args.hasNext()) {
                if (Iterators.elementsEqual(args, Iterators.forArray("the", "news", "?"))) {
                    final MinecraftServer server = world.getServer();
                    if (server == null) {
                        e.typeMessage(SOMETHING_WENT_WRONG);
                        return;
                    }
                    // snooper removed so proxy getter now obf stripped
                    final Proxy proxy = ObfuscationReflectionHelper.getPrivateValue(MinecraftServer.class, server, "field_110456_c");
                    this.remaining = -Math.abs(this.remaining);
                    Futures.addCallback(HTTPUtil.DOWNLOADER_EXECUTOR.submit(() -> News.get(proxy)), new FutureCallback<List<News.Article>>() {
                        @Override
                        public void onSuccess(@Nullable final List<News.Article> result) {
                            if (!e.isAlive()) {
                                return;
                            }
                            AskAlexaBehavior.this.remaining = Math.abs(AskAlexaBehavior.this.remaining);
                            if (result == null || result.isEmpty()) {
                                e.typeMessage(SOMETHING_WENT_WRONG);
                                return;
                            }
                            e.typeMessage("Here's what I found:");
                            for (final ListIterator<News.Article> it = result.listIterator(result.size()); it.hasPrevious(); ) {
                                final News.Article article = it.previous();
                                e.emitChat(new StringTextComponent(String.format("%s: ", article.getDate().format(NEWS_DATE_FORMATTER)))
                                    .setStyle(new Style().setColor(TextFormatting.GRAY))
                                    .appendSibling(new StringTextComponent(article.getTitle()).setStyle(new Style()
                                        .setColor(TextFormatting.BLUE)
                                        .setUnderlined(true)
                                        .setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, article.getUrl().toString()))
                                        .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new StringTextComponent(article.getSubHeader()).setStyle(new Style()
                                            .setItalic(true))
                                        ))
                                    ))
                                );
                            }
                        }

                        @Override
                        public void onFailure(final Throwable t) {
                            if (e.isAlive()) {
                                AskAlexaBehavior.this.remaining = Math.abs(AskAlexaBehavior.this.remaining);
                                e.typeMessage(SOMETHING_WENT_WRONG);
                            }
                        }
                    }, server);
                } else {
                    e.typeMessage(NOT_SURE);
                }
            } else if ("you".equals(first) || Iterators.any(args, "you"::equals)) {
                e.typeMessage("I don't have an opinion that.");
            } else {
                e.typeMessage(TROUBLE_UNDERSTANDING);
            }
        } else {
            e.typeMessage(TROUBLE_UNDERSTANDING);
        }
    }

    @Nullable
    private <T extends IForgeRegistryEntry<T>> T getValue(final IForgeRegistry<T> registry, final String name) {
        final ResourceLocation key = ResourceLocation.tryCreate(name);
        if (key == null) return null;
        if (registry.containsKey(key)) return registry.getValue(key);
        return registry.getEntries().stream()
            .filter(e -> name.equals(e.getKey().getPath()))
            .min(Map.Entry.comparingByKey())
            .map(Map.Entry::getValue)
            .orElse(null);
    }

    private String join(final Iterator<String> it, final String join, final String terminator) {
        final StringBuilder bob = new StringBuilder();
        while (it.hasNext()) {
            final String w = it.next();
            if (terminator.equals(w)) break;
            if (w.length() == 1 && this.deliminators.indexOf(w.charAt(0)) != -1) {
                if (!it.hasNext() && terminator.indexOf(w.charAt(0)) == -1) bob.setLength(0);
                break;
            }
            if (bob.length() > 0) bob.append(join);
            bob.append(w);
        }
        return bob.toString();
    }

    private boolean endsWith(final Iterator<String> it, final String terminators) {
        if (!it.hasNext()) return true;
        final String s = it.next();
        if (it.hasNext()) return false;
        return s.length() == 1 && terminators.indexOf(s.charAt(0)) != -1;
    }

    private String formatTime(final World world, final long time) {
        // f'(x) = 2 / 3 + pi * sin(pi * x) / 6
        // f''(x) = pi * pi / 6 * cos(pi * x)
        final float h = Mth.wrap(world.dimension.calculateCelestialAngle(time, 0.0F), 1.0F) * 24.0F;
        return String.format("%d:%02d %cM", ((int) h + 11) % 12 + 1, (int) (h % 1.0F * 60.0F), h < 12.0F ? 'P' : 'A');
    }

    private boolean togglePower(final BlockPos pos, final boolean recurse) {
        final World world = this.entity.world;
        for (final Direction dir : Direction.values()) {
            final BlockPos n = pos.offset(dir);
            final BlockState state = this.entity.world.getBlockState(n);
            if (state.getBlock() == Blocks.LEVER && getDirection(state) == dir) {
                final FakePlayer fp = FakePlayerFactory.getMinecraft((ServerWorld) world);
                fp.moveToBlockPosAndAngles(pos, 0.0F, 0.0F);
                fp.setHeldItem(Hand.MAIN_HAND, ItemStack.EMPTY);
                fp.setSneaking(false);
                if (state.onBlockActivated(world, fp, Hand.MAIN_HAND, new BlockRayTraceResult(Vec3d.ZERO, dir, n, false)).isSuccess()) {
                    return false;
                }
            } else if (recurse && state.isNormalCube(world, n) && !this.togglePower(n, false)) {
                return false;
            }
        }
        return true;
    }

    // from HorizontalFaceBlock
    private static Direction getDirection(final BlockState state) {
        switch (state.get(HorizontalFaceBlock.FACE)) {
            case CEILING:
                return Direction.DOWN;
            case FLOOR:
                return Direction.UP;
            default:
                return state.get(HorizontalFaceBlock.HORIZONTAL_FACING);
        }
    }

    private SortedSet<BlockPos> findJukebox() {
        return this.findBlock(state -> state.getBlock() == Blocks.JUKEBOX && !state.get(JukeboxBlock.HAS_RECORD));
    }

    private SortedSet<BlockPos> findBlock(final Predicate<BlockState> filter) {
        final BlockPos origin = new BlockPos(this.entity);
        final Vec3i reach = new Vec3i(2, 2, 2);
        final SortedSet<BlockPos> candidates = new TreeSet<>(Comparator.comparing(origin::distanceSq));
        for (final BlockPos pos : BlockPos.getAllInBoxMutable(origin.subtract(reach), origin.add(reach))) {
            if (filter.test(this.entity.world.getBlockState(pos))) {
                candidates.add(pos.toImmutable());
            }
        }
        return candidates;
    }

    private static final class Request {
        final ITextComponent user;

        final Iterable<String> args;

        int time;

        Request(final ITextComponent user, final Iterable<String> args, final int time) {
            this.user = user;
            this.args = args;
            this.time = time;
        }
    }
}
