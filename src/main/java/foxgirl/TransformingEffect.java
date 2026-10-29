package foxgirl;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Идёт превращение. Уровень эффекта = стадия: 0 упал, 1 ноги, 2 грудь и руки, 3 голова, 4 готово (ещё лежит).
 * Клиент по нему рисует игрока лежащим и по частям превращённым.
 */
public class TransformingEffect extends MobEffect {
	public TransformingEffect() { super(MobEffectCategory.NEUTRAL, 0x7A2E3C); }
}
