package foxgirl.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import foxgirl.FoxGirlEntity;
import foxgirl.FoxGirlMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class FoxGirlRenderer extends MobRenderer<FoxGirlEntity, FoxGirlModel> {
	private static final ResourceLocation TEXTURE = new ResourceLocation(FoxGirlMod.MOD_ID, "textures/entity/foxgirl.png");

	public FoxGirlRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new FoxGirlModel(ctx.getResourceManager()), 0.4f);
	}

	@Override
	protected void scale(FoxGirlEntity entity, PoseStack poseStack, float partialTick) {
		float s = entity.getRenderScale();
		if (entity.isLying()) { // лежит на боку рядом с игроком и обнимает
			poseStack.translate(0, -0.42f, 0);
			poseStack.mulPose(Axis.ZP.rotationDegrees(90f));
		}
		poseStack.scale(s, s, s);
		this.shadowRadius = 0.4f * s;
	}

	@Override
	public ResourceLocation getTextureLocation(FoxGirlEntity entity) {
		return TEXTURE;
	}
}
