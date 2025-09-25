package mcheli;

import com.google.common.io.ByteArrayDataInput;
import mcheli.MCH_Config;
import mcheli.MCH_Explosion;
import mcheli.MCH_Lib;
import mcheli.MCH_MOD;
import mcheli.MCH_PacketEffectExplosion;
import mcheli.MCH_PacketIndOpenScreen;
import mcheli.MCH_PacketNotifyLock;
import mcheli.MCH_PacketNotifyServerSettings;
import mcheli.MCH_ServerSettings;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_EntitySeat;
import mcheli.lweapon.MCH_ClientLightWeaponTickHandler;
import mcheli.wrapper.W_Reflection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

public class MCH_CommonPacketHandler {

   public static void onPacketEffectExplosion(EntityPlayer player, ByteArrayDataInput data) {
      if (player.worldObj.isRemote) {
         MCH_PacketEffectExplosion pkt = new MCH_PacketEffectExplosion();
         pkt.readData(data);
         Object exploder = null;
         if (player.getDistanceSq(pkt.prm.posX, pkt.prm.posY, pkt.prm.posZ) <= 40000.0D) {
            if (!pkt.prm.inWater) {
               MCH_Config var10000 = MCH_MOD.config;
               if (!MCH_Config.DefaultExplosionParticle.prmBool) {
                  MCH_Explosion.effectExplosion(player.worldObj, (Entity) exploder, pkt.prm.posX, pkt.prm.posY, pkt.prm.posZ, pkt.prm.size, pkt.prm.isSmoking);
               } else {
                  MCH_Explosion.DEF_effectExplosion(player.worldObj, (Entity) exploder, pkt.prm.posX, pkt.prm.posY, pkt.prm.posZ, pkt.prm.size, pkt.prm.isSmoking);
               }
            } else {
               MCH_Explosion.effectExplosionInWater(player.worldObj, (Entity) exploder, pkt.prm.posX, pkt.prm.posY, pkt.prm.posZ, pkt.prm.size, pkt.prm.isSmoking);
            }
         }

      }
   }

   public static void onPacketIndOpenScreen(EntityPlayer player, ByteArrayDataInput data) {
      if (!player.worldObj.isRemote) {
         MCH_PacketIndOpenScreen pkt = new MCH_PacketIndOpenScreen();
         pkt.readData(data);
         if (pkt.guiID == 3) {
            MCH_EntityAircraft ac = MCH_EntityAircraft.getAircraft_RiddenOrControl(player);
            if (ac != null) {
               ac.openInventory(player);
            }
         } else {
            player.openGui(MCH_MOD.instance, pkt.guiID, player.worldObj, (int) player.posX, (int) player.posY, (int) player.posZ);
         }

      }
   }

   public static void onPacketNotifyServerSettings(EntityPlayer player, ByteArrayDataInput data) {
      if (player.worldObj.isRemote) {
         MCH_Lib.DbgLog(false, "onPacketNotifyServerSettings:" + player, new Object[0]);
         MCH_PacketNotifyServerSettings pkt = new MCH_PacketNotifyServerSettings();
         pkt.readData(data);
         if (!pkt.enableCamDistChange) {
            W_Reflection.setThirdPersonDistance(4.0F);
         }

         MCH_ServerSettings.enableCamDistChange = pkt.enableCamDistChange;
         MCH_ServerSettings.enableEntityMarker = pkt.enableEntityMarker;
         MCH_ServerSettings.enablePVP = pkt.enablePVP;
         MCH_ServerSettings.stingerLockRange = pkt.stingerLockRange;
         MCH_ServerSettings.enableDebugBoundingBox = pkt.enableDebugBoundingBox;
         MCH_ClientLightWeaponTickHandler.lockRange = MCH_ServerSettings.stingerLockRange;
      }
   }

   public static void onPacketNotifyLock(EntityPlayer player, ByteArrayDataInput data) {
      MCH_PacketNotifyLock pkt = new MCH_PacketNotifyLock();
      pkt.readData(data);

      if (!player.worldObj.isRemote) {
         // SERVER: rebroadcast to the pilot + all occupied seats of the targeted aircraft
         if (pkt.entityID >= 0) {
            Entity target = player.worldObj.getEntityByID(pkt.entityID);
            if (target != null) {
               MCH_EntityAircraft ac = null;
               if (target instanceof MCH_EntityAircraft) {
                  ac = (MCH_EntityAircraft) target;
               } else if (target instanceof MCH_EntitySeat) {
                  ac = ((MCH_EntitySeat) target).getParent();
               } else {
                  ac = MCH_EntityAircraft.getAircraft_RiddenOrControl(target);
               }

               if (ac != null && ac.haveFlare() && !ac.isDestroyed()) {
                  // Notify pilot + ALL occupied seats. (Seat ids typically 0..getSeatNum())
                  int seats = ac.getSeatNum();
                  for (int seatId = 0; seatId <= seats; seatId++) {
                     Entity seated = ac.getEntityBySeatId(seatId);
                     if (seated instanceof EntityPlayerMP) {
                        MCH_PacketNotifyLock s = new MCH_PacketNotifyLock();
                        s.entityID = ac.getEntityId(); // identify THIS aircraft
                        mcheli.wrapper.W_Network.sendToPlayer(s, (EntityPlayerMP) seated);
                     }
                  }
               }
            }
         }
      } else {
         // CLIENT: defer to client proxy (no direct client classes here!)
         // The proxy will decide whether the local player is actually in the targeted aircraft
         // and trigger the lock tone only if so.
         MCH_MOD.proxy.onNotifyLockClient(pkt.entityID);
      }
   }


}