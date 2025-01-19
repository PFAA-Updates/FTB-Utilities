package ftb.utils.mod.handlers;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;

import com.google.gson.JsonObject;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import ftb.lib.LMNBTUtils;
import ftb.utils.api.EventLMWorldServer;
import ftb.utils.mod.FTBU;
import ftb.utils.mod.config.FTBUConfigGeneral;
import ftb.utils.world.*;
import ftb.utils.world.claims.ClaimedChunks;
import latmod.lib.*;
import latmod.lib.util.Phase;

public class FTBUWorldEventHandler // FTBLIntegration
{

    @SubscribeEvent
    public void worldLoaded(net.minecraftforge.event.world.WorldEvent.Load e) {
        if (e.world instanceof WorldServer) FTBUChunkEventHandler.instance.markDirty(e.world);

        // Move /world/latmod/LMPlayers.txt to /world/LatMod/LMPlayers.txt
        try {
            Path worldPath = Paths.get(e.world.getSaveHandler().getWorldDirectoryName());
            Path oldFile = worldPath.resolve("latmod").resolve("LMPlayers.txt");
            Path newFile = worldPath.resolve("LatMod").resolve("LMPlayers.txt");
            if (oldFile.toFile().exists() && !newFile.toFile().exists()) {
                FTBU.logger.info("Attempting to move " + oldFile + " to " + newFile);
                Files.move(oldFile, newFile);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @SubscribeEvent
    public void worldSaved(net.minecraftforge.event.world.WorldEvent.Save e) {
        if (e.world.provider.dimensionId == 0 && e.world instanceof WorldServer) {
            new EventLMWorldServer.Saved(LMWorldServer.inst).post();

            JsonObject group = new JsonObject();
            LMWorldServer.inst.save(group, Phase.PRE);

            NBTTagCompound tag = new NBTTagCompound();
            NBTTagCompound players = new NBTTagCompound();
            LMWorldServer.inst.writePlayersToServer(players);
            tag.setTag("Players", players);
            tag.setInteger("LastID", LMPlayerServer.lastPlayerID);
            LMNBTUtils.writeMap(new File(LMWorldServer.inst.latmodFolder, "LMPlayers.dat"), tag);

            LMWorldServer.inst.save(group, Phase.POST);
            LMJsonUtils.toJson(new File(LMWorldServer.inst.latmodFolder, "LMWorld.json"), group);

            group = new JsonObject();
            LMWorldServer.inst.claimedChunks.save(group);
            LMJsonUtils.toJson(new File(LMWorldServer.inst.latmodFolder, "ClaimedChunks.json"), group);

            // Export player list //

            try {
                ArrayList<String> l = new ArrayList<>();
                int[] list = LMWorldServer.inst.getAllPlayerIDs();
                Arrays.sort(list);

                for (int i = 0; i < list.length; i++) {
                    LMPlayer p = LMWorldServer.inst.getPlayer(list[i]);

                    StringBuilder sb = new StringBuilder();
                    sb.append(LMStringUtils.fillString(Integer.toString(p.getPlayerID()), ' ', 6));
                    sb.append(LMStringUtils.fillString(p.getProfile().getName(), ' ', 21));
                    sb.append(p.getStringUUID());
                    l.add(sb.toString());
                }

                LMFileUtils.save(new File(LMWorldServer.inst.latmodFolder, "LMPlayers.txt"), l);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @SubscribeEvent
    public void onMobSpawned(net.minecraftforge.event.entity.EntityJoinWorldEvent e) {
        if (!e.world.isRemote && !isEntityAllowed(e.entity)) {
            e.entity.setDead();
            e.setCanceled(true);
        }
    }

    private boolean isEntityAllowed(Entity e) {
        if (e instanceof EntityPlayer) return true;

        if (FTBUConfigGeneral.isEntityBanned(e.getClass())) return false;

        if (FTBUConfigGeneral.safe_spawn.getAsBoolean() && ClaimedChunks.isInSpawnD(e.dimension, e.posX, e.posZ)) {
            if (e instanceof IMob) return false;
            else if (e instanceof EntityChicken && e.riddenByEntity != null) return false;
        }

        return true;
    }

    @SubscribeEvent
    public void onExplosionStart(net.minecraftforge.event.world.ExplosionEvent.Start e) {
        if (e.world.isRemote) return;
        int dim = e.world.provider.dimensionId;
        int cx = MathHelperLM.chunk(e.explosion.explosionX);
        int cz = MathHelperLM.chunk(e.explosion.explosionZ);
        if (!LMWorldServer.inst.claimedChunks.allowExplosion(dim, cx, cz)) e.setCanceled(true);
    }
}
