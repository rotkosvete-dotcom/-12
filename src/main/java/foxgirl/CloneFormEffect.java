package foxgirl;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Облик клона Фурритян: быстрее, но хрупче (14 HP). Остальные способности — в ClonePerks. */
public class CloneFormEffect extends MobEffect {
	public CloneFormEffect() {
		super(MobEffectCategory.NEUTRAL, 0xA83A4A);
		this.addAttributeModifier(Attributes.MOVEMENT_SPEED, "5a9d3c64-8b0e-4f5e-9e0c-1d7a2b3c4e51", 0.10, AttributeModifier.Operation.MULTIPLY_TOTAL);
		this.addAttributeModifier(Attributes.MAX_HEALTH, "c2f1e7a8-3b94-4d6f-a1d2-7e8f9a0b1c32", -6.0, AttributeModifier.Operation.ADDITION);
	}
}
