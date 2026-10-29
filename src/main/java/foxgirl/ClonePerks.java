package foxgirl;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * Способности игрока в облике клона Фурритян:
 *  - быстрее (+10%), но 14 HP (эффект CloneFormEffect);
 *  - уменьшается: обычно ~1 блок ростом, а на Shift — совсем крошечный (событие размера в ModEvents);
 *  - хвост: его удары задевают всех рядом с целью (размах с отбрасыванием);
 *  - ярость: при низком здоровье — Сила, Скорость, Сопротивление на 20 с и ударная волна (раз в 2 минуты);
 *  - разведка: ночью подсвечивает монстров вокруг;
 *  - дикая Фурритян его не трогает.
 */
public final class ClonePerks {
	private static boolean sweeping = false;

	private ClonePerks() {}

	public static boolean isClone(Player p) { return p.hasEffect(FoxGirlMod.CLONE_FORM.get()); }

	public static void tick(ServerPlayer p) {
		if (!isClone(p) || p.tickCount % 100 != 0) return;
		if (p.level().isNight()) {
			for (Monster m : p.level().getEntitiesOfClass(Monster.class, p.getBoundingBox().inflate(16.0))) {
				m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0, true, false));
			}
		}
	}

	/** Удар клона хвостом по площади. */
	public static void onAttack(LivingHurtEvent e) {
		if (sweeping) return;
		if (!(e.getSource().getEntity() instanceof Player p) || !isClone(p) || e.getSource().getDirectEntity() != p) return;
		LivingEntity victim = e.getEntity();
		if (victim == p) return;
		sweeping = true;
		try {
			for (LivingEntity o : p.level().getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(2.5),
				x -> x != p && x != victim && x.isAlive() && !(x instanceof Player) && !(x instanceof FoxGirlEntity f && f.isTame()))) {
				o.hurt(p.damageSources().playerAttack(p), Math.max(1.0f, e.getAmount() * 0.6f));
				o.knockback(0.8, p.getX() - o.getX(), p.getZ() - o.getZ());
			}
		} finally {
			sweeping = false;
		}
		if (p.level() instanceof ServerLevel sl) {
			sl.sendParticles(ParticleTypes.SWEEP_ATTACK, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(), 1, 0, 0, 0, 0);
		}
	}

	/** Ярость при низком здоровье. */
	public static void onVictim(ServerPlayer p, LivingHurtEvent e) {
		if (!isClone(p) || p.getHealth() - e.getAmount() > 6.0f) return;
		long now = p.level().getGameTime();
		if (now < p.getPersistentData().getLong("foxgirl_rage_until")) return;
		p.getPersistentData().putLong("foxgirl_rage_until", now + 2400);

		p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 1));
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 1));
		p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 0));
		for (LivingEntity o : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(5.0),
			x -> x != p && x.isAlive() && !(x instanceof Player) && !(x instanceof FoxGirlEntity f && f.isTame()))) {
			o.knockback(1.2, p.getX() - o.getX(), p.getZ() - o.getZ());
			o.hurtMarked = true;
		}
		p.level().playSound(null, p.blockPosition(), SoundEvents.RAVAGER_ROAR, p.getSoundSource(), 1.4f, 1.1f);
		if (p.level() instanceof ServerLevel sl) {
			sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, p.getX(), p.getY() + 0.6, p.getZ(), 12, 0.4, 0.3, 0.4, 0.0);
		}
		p.displayClientMessage(Component.translatable("message.foxgirl.clone_rage"), true);
	}
}
