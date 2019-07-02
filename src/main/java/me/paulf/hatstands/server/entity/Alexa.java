package me.paulf.hatstands.server.entity;

import com.google.common.base.Splitter;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.Iterators;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import me.paulf.hatstands.util.Mth;
import net.minecraft.block.Block;
import net.minecraft.block.BlockJukebox;
import net.minecraft.block.BlockLever;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityTracker;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemRecord;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketEntityEquipment;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.HttpUtil;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.net.Proxy;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;

class Alexa implements Runnable {
	private final HatStandEntity entity;

	private final Splitter argSplitter = Splitter.on(Pattern.compile(" |(?=[!,.;?])")).omitEmptyStrings();

	private final EvictingQueue<Request> requests = EvictingQueue.create(16);

	private int remaining = 0;

	Alexa(final HatStandEntity entity) {
		this.entity = entity;
	}

	private void enqueue(final Request request) {
		this.requests.offer(request);
	}

	@Override
	public void run() {
		final Request pending = this.requests.peek();
		if (pending != null) {
			if (pending.time == 6) {
				final EntityTracker tracker = ((WorldServer) this.entity.world).getEntityTracker();
				final ItemStack ring = new ItemStack(Items.LEATHER_HELMET);
				((ItemArmor) ring.getItem()).setColor(ring, "Picton BluE OBtAInEd".hashCode() & 0xFFFFFF);
				tracker.sendToTracking(this.entity, new SPacketEntityEquipment(this.entity.getEntityId(), EntityEquipmentSlot.HEAD, ring));
				this.entity.playSound(SoundEvents.BLOCK_NOTE_CHIME, 0.125F, 0.668F);
				this.remaining = 32;
			}
			if (pending.time-- <= 0) {
				this.handle(pending.user, pending.args.iterator());
				this.requests.poll();
			}
		}
		if (this.remaining != 0) {
			if (this.remaining > 0) {
				this.remaining--;
				if (this.remaining == 0) {
					final EntityTracker tracker = ((WorldServer) this.entity.world).getEntityTracker();
					tracker.sendToTracking(this.entity, new SPacketEntityEquipment(this.entity.getEntityId(), EntityEquipmentSlot.HEAD, this.entity.getItemStackFromSlot(EntityEquipmentSlot.HEAD)));
					this.entity.playSound(SoundEvents.BLOCK_NOTE_CHIME, 0.125F, 0.0F);
				}
			} else if (this.remaining < -1) {
				this.remaining++;
			}
		}
	}

	@SubscribeEvent
	public void onChat(final ServerChatEvent event) {
		final EntityPlayerMP player = event.getPlayer();
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
				if (!args.hasNext() && ("morning".equals(second) || "afternoon".equals(second) || "evening".equals(second))) {
					e.typeMessage(String.format("Good %s.", second.toLowerCase(Locale.ROOT)));
				} else {
					e.typeMessage(TROUBLE_UNDERSTANDING);
				}
			} else if ("flip".equals(first)) {
				if (Iterators.elementsEqual(args, Iterators.forArray("a", "coin"))) {
					e.typeMessage(e.getRNG().nextBoolean() ? "Heads": "Tails");
				} else {
					e.typeMessage(TROUBLE_UNDERSTANDING);
				}
			} else if ("play".equals(first) && args.hasNext()) {
				final String second = args.next();
				final Item item;
				if (!args.hasNext() && (item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("record_" + second.toLowerCase(Locale.ROOT)))) instanceof ItemRecord) {
					final SortedSet<BlockPos> candidates = this.findJukebox();
					if (candidates.isEmpty()) {
						e.typeMessage("I can't find any music in your library.");
					} else {
						world.playEvent(RECORD_EVENT, candidates.first(), Item.getIdFromItem(item));
					}
				} else {
					e.typeMessage(String.format("I can't find the song '%s.'", second));
				}
			} else if ("stop".equals(first)) {
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
					if ("lamp".equals(thing) && (!args.hasNext() || ".".equals(args.next()) && !args.hasNext())) {
						final Block block = "on".equals(second) ?  Blocks.REDSTONE_LAMP :  Blocks.LIT_REDSTONE_LAMP;
						final SortedSet<BlockPos> candidates = this.findBlock(state -> state.getBlock() == block);
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
						if (biome.canRain() || biome.getEnableSnow()) {
							if (world.getBiomeProvider().getTemperatureAtHeight(biome.getTemperature(pos), world.getPrecipitationHeight(pos).getY()) >= 0.15F) {
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
								if (world.getGameRules().getBoolean("doWeatherCycle")) {
									final long tick = info.getWorldTime();
									final long expected = Math.max(info.getCleanWeatherTime(), info.getRainTime());
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
						} else if (world.getGameRules().getBoolean("doWeatherCycle")) {
							final long from = "today".equals(day) ? 0 : DAY;
							final long tick = info.getWorldTime();
							final long expected = Math.max(info.getCleanWeatherTime(), info.getRainTime());
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
					e.typeMessage(new TextComponentString("You are ").appendSibling(user).appendText("."));
				} else {
					e.typeMessage(NOT_SURE);
				}
			} else if ("what".equals(first)) {
				if (Iterators.elementsEqual(args, Iterators.forArray("time", "is", "it", "?"))) {
					e.typeMessage(String.format("It is %s.", this.formatTime(world, world.getWorldTime())));
				} else {
					e.typeMessage(NOT_SURE);
				}
			} else if ("what's".equals(first) && args.hasNext()) {
				if (Iterators.elementsEqual(args, Iterators.forArray("the", "news", "?"))) {
					final MinecraftServer server = world.getMinecraftServer();
					if (server == null) {
						e.typeMessage(SOMETHING_WENT_WRONG);
						return;
					}
					final Proxy proxy = server.getServerProxy();
					this.remaining = -Math.abs(this.remaining);
					Futures.addCallback(HttpUtil.DOWNLOADER_EXECUTOR.submit(() -> News.get(proxy)), new FutureCallback<List<News.Article>>() {
						@Override
						public void onSuccess(final @Nullable List<News.Article> result) {
							if (!e.isEntityAlive()) {
								return;
							}
							Alexa.this.remaining = 1;
							if (result == null || result.isEmpty()) {
								e.typeMessage(SOMETHING_WENT_WRONG);
								return;
							}
							e.typeMessage("Here's what I found:");
							for (final ListIterator<News.Article> it = result.listIterator(result.size()); it.hasPrevious(); ) {
								final News.Article article = it.previous();
								final DateTimeFormatter formatter = new DateTimeFormatterBuilder()
									.appendText(MONTH_OF_YEAR)
									.appendLiteral(' ')
									.appendText(DAY_OF_MONTH)
									.toFormatter(Locale.US);
								e.emitChat(new TextComponentString(String.format("%s: ", article.getDate().format(formatter)))
									.setStyle(new Style().setColor(TextFormatting.GRAY))
									.appendSibling(new TextComponentString(article.getTitle()).setStyle(new Style()
										.setColor(TextFormatting.BLUE)
										.setUnderlined(true)
										.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, article.getUrl().toString()))
										.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(article.getSubHeader()).setStyle(new Style()
											.setItalic(true))
										))
									))
								);
							}
						}

						@Override
						public void onFailure(final Throwable t) {
							if (e.isEntityAlive()) {
								Alexa.this.remaining = Math.abs(Alexa.this.remaining);
								e.typeMessage(SOMETHING_WENT_WRONG);
							}
						}

					}, server::addScheduledTask);
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

	private String formatTime(final World world, final long time) {
		// f'(x) = 2 / 3 + pi * sin(pi * x) / 6
		// f''(x) = pi * pi / 6 * cos(pi * x)
		final float h = Mth.wrap(world.provider.calculateCelestialAngle(time, 0.0F), 1.0F) * 24.0F;
		return String.format("%d:%02d %cM", ((int) h + 11) % 12 + 1, (int) (h % 1.0F * 60.0F), h < 12.0F ? 'P' : 'A');
	}

	private boolean togglePower(final BlockPos pos, final boolean recurse) {
		final World world = this.entity.world;
		for (final EnumFacing dir : EnumFacing.VALUES) {
			final BlockPos n = pos.offset(dir);
			final IBlockState state = this.entity.world.getBlockState(n);
			if (state.getBlock() == Blocks.LEVER && state.getValue(BlockLever.FACING).getFacing() == dir) {
				final FakePlayer fp = FakePlayerFactory.getMinecraft((WorldServer) world);
				fp.moveToBlockPosAndAngles(pos, 0.0F, 0.0F);
				fp.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
				fp.setSneaking(false);
				if (state.getBlock().onBlockActivated(world, n, state, fp, EnumHand.MAIN_HAND, dir, 0.0F, 0.0F, 0.0F)) {
					return false;
				}
			} else if (recurse && state.isNormalCube() && !this.togglePower(n, false)) {
				return false;
			}
		}
		return true;
	}

	private SortedSet<BlockPos> findJukebox() {
		return this.findBlock(state -> state.getBlock() == Blocks.JUKEBOX && !state.getValue(BlockJukebox.HAS_RECORD));
	}

	private SortedSet<BlockPos> findBlock( final Predicate<IBlockState> filter) {
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
