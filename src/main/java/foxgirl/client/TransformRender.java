package foxgirl.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import foxgirl.FoxGirlMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/**
 * Рисует игрока вместо стандартной модели, когда он превращается или уже клон:
 *  - эффект «Превращение» (уровень = стадия): игрок лежит на спине;
 *      стадия 0 — человек, 1 — ноги Фурритян, 2 — + грудь и руки (и хвост), 3 — + голова, 4 — вся Фурритян;
 *  - эффект «Клон Фурритян»: стоит маленькой Фурритян (на Shift — приседает).
 */
@Mod.EventBusSubscriber(modid = FoxGirlMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class TransformRender {
	private static FoxGirlModel foxModel;
	private static final ResourceLocation FOX_TEXTURE = new ResourceLocation(FoxGirlMod.MOD_ID, "textures/entity/foxgirl.png");

	private static final Set<String> LEGS = Set.of("LeftLeg", "RightLeg");
	private static final Set<String> TORSO = Set.of("Body", "Boobs", "LeftBoob", "RightBoob",
		"LeftArms", "leftarm_1", "leftarm_2", "RightArms", "rightarm_1", "rightarm_2", "Tail");
	private static final Set<String> HEAD = Set.of("Head", "LeftEye", "RightEye", "LeftEar", "RightEar");

	@SubscribeEvent
	public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
		if (!(event.getEntity() instanceof AbstractClientPlayer player)) return;
		MobEffectInstance transforming = player.getEffect(FoxGirlMod.TRANSFORMING.get());
		boolean clone = player.hasEffect(FoxGirlMod.CLONE_FORM.get());
		if (transforming == null && !clone) return;

		event.setCanceled(true); // стандартную модель не рисуем

		if (foxModel == null) foxModel = new FoxGirlModel(Minecraft.getInstance().getResourceManager());
		PoseStack ps = event.getPoseStack();
		float partial = event.getPartialTick();
		float age = player.tickCount + partial;
		int light = event.getPackedLight();

		VertexConsumer foxVc = event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(FOX_TEXTURE));

		ps.pushPose();
		if (transforming != null) {
			int stage = transforming.getAmplifier();
			// лежит на спине, лицом вверх (ориентация фиксированная, чтобы Фурритян могла лечь рядом)
			ps.translate(0, 0.12, 0);
			ps.mulPose(Axis.YP.rotationDegrees(180f));
			ps.mulPose(Axis.XP.rotationDegrees(-90f));
			float s = 0.9375f; // как у обычной модели игрока
			ps.scale(-s, -s, s);
			ps.translate(0, -1.501f, 0);

			boolean legsFox = stage >= 1, torsoFox = stage >= 2, headFox = stage >= 3;

			if (stage < 4) {
				PlayerModel<AbstractClientPlayer> hm = event.getRenderer().getModel();
				hm.setAllVisible(true);
				hm.young = false;
				hm.riding = false;
				hm.crouching = false;
				hm.attackTime = 0f;
				hm.setupAnim(player, 0f, 0f, age, 0f, 0f);
				hm.leftLeg.visible = hm.rightLeg.visible = hm.leftPants.visible = hm.rightPants.visible = !legsFox;
				hm.body.visible = hm.jacket.visible = hm.leftArm.visible = hm.rightArm.visible
					= hm.leftSleeve.visible = hm.rightSleeve.visible = !torsoFox;
				hm.head.visible = hm.hat.visible = !headFox;
				VertexConsumer humanVc = event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(player.getSkinTextureLocation()));
				hm.renderToBuffer(ps, humanVc, light, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
			}

			Set<String> only = new HashSet<>();
			if (legsFox) only.addAll(LEGS);
			if (torsoFox) only.addAll(TORSO);
			if (headFox) only.addAll(HEAD);
			if (!only.isEmpty()) {
				foxModel.animate(0f, 0f, age, 0f, 0f, false, false, 0);
				foxModel.renderOnly(ps, foxVc, light, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f, only);
			}
		} else {
			// стоящий клон: маленькая Фурритян
			float yaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
			float headYaw = Mth.clamp(Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot) - yaw, -75f, 75f);
			float pitch = Mth.lerp(partial, player.xRotO, player.getXRot());
			float moving = Math.min(1f, (float) player.getDeltaMovement().horizontalDistance() * 4f);
			float s = player.isCrouching() ? 0.3f : 0.5f;
			ps.mulPose(Axis.YP.rotationDegrees(180f - yaw));
			ps.scale(-s, -s, s);
			ps.translate(0, -1.501f, 0);
			foxModel.animate(age * 0.9f, moving, age, headYaw, pitch, player.isCrouching(), false, 0);
			foxModel.renderToBuffer(ps, foxVc, light, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
		}
		ps.popPose();
	}
}
