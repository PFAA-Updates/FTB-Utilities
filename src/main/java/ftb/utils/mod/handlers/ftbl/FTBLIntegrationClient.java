package ftb.utils.mod.handlers.ftbl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.*;
import ftb.lib.api.*;
import ftb.lib.api.client.*;
import ftb.lib.api.client.model.CubeRenderer;
import ftb.lib.api.notification.ClientNotifications;
import ftb.lib.mod.FTBLibMod;
import ftb.utils.api.EventLMWorldClient;
import ftb.utils.api.guide.ClientGuideFile;
import ftb.utils.badges.ClientBadges;
import ftb.utils.mod.client.gui.claims.ClaimedAreasClient;
import ftb.utils.world.LMWorldClient;
import ftb.utils.world.claims.WorldBorder;
import latmod.lib.*;

@SideOnly(Side.CLIENT)
public class FTBLIntegrationClient extends FTBLIntegration {

    public static final ResourceLocation world_border_tex = new ResourceLocation(
            "ftbu",
            "textures/map/world_border.png");
    private static final CubeRenderer worldBorderRenderer = new CubeRenderer();

    public void onReloaded(EventFTBReload e) {
        super.onReloaded(e);

        if (e.world.side.isClient()) {
            FTBLibClient.clearCachedData();
            ClientBadges.clearPlayerBadges();
            ClientGuideFile.instance.reload(e);
        }
    }

    public void onFTBWorldClient(EventFTBWorldClient e) {
        ClientNotifications.init();
        ClaimedAreasClient.clear();

        if (e.world == null) {
            if (LMWorldClient.inst != null) new EventLMWorldClient(LMWorldClient.inst, true).post();
            LMWorldClient.inst = null;
        }
    }

    public void readWorldData(ByteIOStream io) {
        LMWorldClient.inst = new LMWorldClient(io.readInt());
        LMWorldClient.inst.readDataFromNet(io, true);
        FTBLibMod.logger.info("Joined the server with PlayerID " + LMWorldClient.inst.clientPlayerID);
        new EventLMWorldClient(LMWorldClient.inst, false).post();
    }

    public boolean hasClientWorld() {
        return LMWorldClient.inst != null && LMWorldClient.inst.clientPlayerID > 0
                && LMWorldClient.inst.clientPlayer != null;
    }

    public void renderWorld(float pt) {
        if (!FTBLibClient.isIngameWithFTBU() || !LMWorldClient.inst.settings.border_enabled.getAsBoolean()) return;

        WorldBorder wb = LMWorldClient.inst.settings.getWB(FTBLibClient.getDim());
        int s = wb.getSize();
        if (s <= 0) return;

        double minX = (MathHelperLM.chunk(-s + wb.pos.x) + 1D) * 16D + 0.01D;
        double maxX = MathHelperLM.chunk(s + wb.pos.x) * 16D - 0.01D;
        double minZ = (MathHelperLM.chunk(-s + wb.pos.y) + 1D) * 16D + 0.01D;
        double maxZ = MathHelperLM.chunk(s + wb.pos.y) * 16D - 0.01D;

        double rd = 32D;

        boolean renderWest = LMFrustrumUtils.playerX <= minX + rd;
        boolean renderEast = LMFrustrumUtils.playerX >= maxX - rd;
        boolean renderNorth = LMFrustrumUtils.playerZ <= minZ + rd;
        boolean renderSouth = LMFrustrumUtils.playerZ >= maxZ - rd;

        GlStateManager.pushAttrib();
        GlStateManager.enableBlend();
        GlStateManager.disableLighting();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableTexture2D();
        FTBLibClient.pushMaxBrightness();
        FTBLibClient.setTexture(world_border_tex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

        GlStateManager.pushMatrix();
        GlStateManager.translate(-LMFrustrumUtils.renderX, -LMFrustrumUtils.renderY, -LMFrustrumUtils.renderZ);

        double f = (Minecraft.getSystemTime() * 0.0005D) % 1D;

        worldBorderRenderer.setTessellator(Tessellator.instance);
        worldBorderRenderer.setSize(minX, 0D, minZ, maxX, 256D, maxZ);
        worldBorderRenderer.setUV(minX + f, 0D, maxX + f, 256D);

        // GL11.glAlphaFunc();

        float maxA = 0.6F;

        GlStateManager.color(1F, 1F, 1F, maxA);

        if (renderWest) {
            GlStateManager.color(1F, 1F, 1F, maxA - (float) ((LMFrustrumUtils.playerX - minX) * maxA / rd));
            worldBorderRenderer.renderWest();
        }

        if (renderEast) {
            GlStateManager.color(1F, 1F, 1F, maxA - (float) ((maxX - LMFrustrumUtils.playerX) * maxA / rd));
            worldBorderRenderer.renderEast();
        }

        if (renderNorth) {
            GlStateManager.color(1F, 1F, 1F, maxA - (float) ((LMFrustrumUtils.playerZ - minZ) * maxA / rd));
            worldBorderRenderer.renderNorth();
        }

        if (renderSouth) {
            GlStateManager.color(1F, 1F, 1F, maxA - (float) ((maxZ - LMFrustrumUtils.playerZ) * maxA / rd));
            worldBorderRenderer.renderSouth();
        }

        GlStateManager.popMatrix();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        FTBLibClient.popMaxBrightness();
        GlStateManager.depthMask(true);
        GlStateManager.popAttrib();
    }

    public void onTooltip(ItemTooltipEvent e) {}
}
