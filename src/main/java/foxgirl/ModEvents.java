package foxgirl;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.NoteBlockEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public class ModEvents {
	@Mod.EventBusSubscriber(modid = FoxGirlMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
	public static class ModBus {
		@SubscribeEvent
		public static void onAttributes(EntityAttributeCreationEvent event) {
			event.put(FoxGirlMod.FOX_GIRL.get(), FoxGirlEntity.createAttributes().build());
		}
	}

	@Mod.EventBusSubscriber(modid = FoxGirlMod.MOD_ID)
	public static class ForgeBus {
		/** Пластинка в проигрыватель — Фурритян слышит и идёт на звук (30 с). */
		@SubscribeEvent
		public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
			Level level = event.getLevel();
			if (level.isClientSide) return;
			BlockState st = level.getBlockState(event.getPos());
			ItemStack held = event.getItemStack();
			if (st.is(Blocks.JUKEBOX) && held.getItem() instanceof RecordItem && !st.getValue(JukeboxBlock.HAS_RECORD)) {
				Lures.add(level, event.getPos(), 600);
			}
		}

		/** Нотный блок — короткий шум (10 с). */
		@SubscribeEvent
		public static void onNote(NoteBlockEvent.Play event) {
			if (event.getLevel() instanceof Level level && !level.isClientSide()) {
				Lures.add(level, event.getPos(), 200);
			}
		}

		/** Дикая Фурритян не убивает игрока: добивающий удар заменяется захватом. Пока несёт — игрок неуязвим. */
		@SubscribeEvent
		public static void onPlayerHurt(LivingHurtEvent event) {
			if (!(event.getEntity() instanceof ServerPlayer sp)) return;
			ClonePerks.onVictim(sp, event);
			if (sp.getVehicle() instanceof FoxGirlEntity carrier && carrier.isCarrying()
				&& !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				event.setCanceled(true);
				return;
			}
			if (event.getSource().getEntity() instanceof FoxGirlEntity fox && !fox.isTame() && !fox.isKitten()
				&& fox.level().dimension().equals(Backrooms.DIMENSION)) {
				float hp = sp.getHealth();
				float amount = event.getAmount();
				if (hp - amount <= FoxGirlEntity.LOW_HP) {
					event.setAmount(Math.max(0f, Math.min(amount, hp - 1f))); // оставляем минимум 1 HP
					fox.grabPlayer(sp);
				}
			}
		}

		/** Удар игрока-клона хвостом задевает всех рядом с целью. */
		@SubscribeEvent
		public static void onCloneAttack(LivingHurtEvent event) {
			ClonePerks.onAttack(event);
		}

		/** Размер игрока: лежащий при превращении (низкая камера), маленький клон (на Shift — крошечный). */
		@SubscribeEvent
		public static void onSize(EntityEvent.Size event) {
			if (!(event.getEntity() instanceof Player p)) return;
			if (p.hasEffect(FoxGirlMod.TRANSFORMING.get())) {
				event.setNewSize(EntityDimensions.fixed(0.6f, 0.6f));
				event.setNewEyeHeight(0.35f);
			} else if (p.hasEffect(FoxGirlMod.CLONE_FORM.get())) {
				float s = event.getPose() == Pose.CROUCHING ? 0.35f : 0.55f;
				event.setNewSize(event.getNewSize().scale(s));
				event.setNewEyeHeight(event.getNewEyeHeight() * s);
			}
		}

		private static final Map<Player, Integer> FORMS = new WeakHashMap<>();

		/** Пересчитывает размер при смене облика (на обеих сторонах) и крутит способности клона. */
		@SubscribeEvent
		public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
			if (event.phase != TickEvent.Phase.END) return;
			Player p = event.player;
			int form = p.hasEffect(FoxGirlMod.TRANSFORMING.get()) ? 1 : p.hasEffect(FoxGirlMod.CLONE_FORM.get()) ? 2 : 0;
			Integer last = FORMS.put(p, form);
			if ((last == null ? 0 : last) != form) p.refreshDimensions();

			if (p.level().isClientSide || !(p instanceof ServerPlayer sp)) return;
			ClonePerks.tick(sp);
			// страховка: превращение без Фурритян рядом (она погибла/пропала) — снимаем
			if (form == 1 && sp.tickCount % 40 == 0) {
				boolean owned = !sp.serverLevel().getEntitiesOfClass(FoxGirlEntity.class, sp.getBoundingBox().inflate(32.0),
					f -> f.isRitualFor(sp.getUUID())).isEmpty();
				if (!owned) {
					sp.removeEffect(FoxGirlMod.TRANSFORMING.get());
					sp.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
				}
			}
		}

		/** Пока она несёт игрока домой, спрыгнуть (Shift) нельзя. */
		@SubscribeEvent
		public static void onDismount(EntityMountEvent event) {
			if (event.isDismounting() && !event.getEntityMounting().level().isClientSide
				&& event.getEntityBeingMounted() instanceof FoxGirlEntity fox && fox.isCarrying()
				&& event.getEntityMounting() instanceof ServerPlayer sp && sp.isAlive() && sp.isShiftKeyDown()) {
				event.setCanceled(true);
			}
		}

		/** Новый мир: строим квартиру на спавне. Старые миры не трогаем (для них есть /apartment). */
		@SubscribeEvent
		public static void onServerStarted(ServerStartedEvent event) {
			ServerLevel overworld = event.getServer().overworld();
			ApartmentData d = Apartment.data(overworld);
			if (d.built || overworld.getGameTime() > 200) return;
			d.spawn = Apartment.build(overworld, overworld.getSharedSpawnPos());
			d.built = true;
			d.setDirty();
			overworld.setDefaultSpawnPos(d.spawn, 0f);
		}

		/** Каждый новый игрок первый раз появляется в квартире. */
		@SubscribeEvent
		public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
			if (!(event.getEntity() instanceof ServerPlayer sp)) return;
			ServerLevel overworld = sp.server.overworld();
			ApartmentData d = Apartment.data(overworld);
			if (!d.built || !d.greeted.add(sp.getUUID())) return;
			d.setDirty();
			BlockPos p = d.spawn;
			sp.teleportTo(overworld, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
			sp.setRespawnPosition(net.minecraft.world.level.Level.OVERWORLD, p, 0f, true, false);
		}

		@SubscribeEvent
		public static void onServerStopping(ServerStoppingEvent event) { Lures.clear(); }

		private static int choose(CommandSourceStack src, boolean asClone) throws CommandSyntaxException {
			ServerPlayer p = src.getPlayerOrException();
			List<FoxGirlEntity> foxes = p.serverLevel().getEntitiesOfClass(FoxGirlEntity.class, p.getBoundingBox().inflate(32.0),
				f -> f.isAwaitingChoice(p.getUUID()));
			if (foxes.isEmpty()) {
				p.sendSystemMessage(Component.translatable("message.foxgirl.no_choice"));
				return 0;
			}
			foxes.get(0).resolveChoice(p, asClone);
			return 1;
		}

		/** /foxgirl choose clone|reset — выбор после превращения (по клику в чате); /foxgirl human — вернуть облик. */
		@SubscribeEvent
		public static void onFoxgirlCommand(RegisterCommandsEvent event) {
			event.getDispatcher().register(Commands.literal("foxgirl")
				.then(Commands.literal("choose")
					.then(Commands.literal("clone").executes(ctx -> choose(ctx.getSource(), true)))
					.then(Commands.literal("reset").executes(ctx -> choose(ctx.getSource(), false))))
				.then(Commands.literal("human").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					p.removeEffect(FoxGirlMod.CLONE_FORM.get());
					p.removeEffect(FoxGirlMod.TRANSFORMING.get());
					p.sendSystemMessage(Component.translatable("message.foxgirl.human"));
					return 1;
				})));
		}

		/** /apartment — построить квартиру там, где стоишь (для старых миров). */
		@SubscribeEvent
		public static void onApartmentCommand(RegisterCommandsEvent event) {
			event.getDispatcher().register(Commands.literal("apartment")
				.requires(src -> src.hasPermission(2))
				.executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					ServerLevel level = player.serverLevel();
					BlockPos spawn = Apartment.build(level, player.blockPosition());
					player.teleportTo(level, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0f, 0f);
					player.displayClientMessage(Component.translatable("message.foxgirl.apartment_built"), true);
					return 1;
				}));
		}

		/** /backrooms — телепорт туда и обратно (для операторов, удобно для проверки). */
		@SubscribeEvent
		public static void onCommands(RegisterCommandsEvent event) {
			event.getDispatcher().register(Commands.literal("backrooms")
				.requires(src -> src.hasPermission(2))
				.executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					Backrooms.toggle(player);
					return 1;
				}));
		}
	}
}
