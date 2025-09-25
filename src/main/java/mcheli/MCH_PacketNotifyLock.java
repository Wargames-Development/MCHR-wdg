package mcheli;

import com.google.common.io.ByteArrayDataInput;
import java.io.DataOutputStream;
import java.io.IOException;

import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.MCH_Packet;
import mcheli.wrapper.W_Network;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

public class MCH_PacketNotifyLock extends MCH_Packet {

   /** The targeted aircraft (or seat parent) entity id. -1 means "unspecified/ignore". */
   public int entityID = -1;

   @Override
   public int getMessageID() {
      return 536873984;
   }

   @Override
   public void readData(ByteArrayDataInput data) {
      try {
         this.entityID = data.readInt();
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   @Override
   public void writeData(DataOutputStream dos) {
      try {
         dos.writeInt(this.entityID);
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   /** Client -> Server: notify server that this target has been locked. */
   public static void send(Entity target) {
      if (target != null) {
         MCH_PacketNotifyLock s = new MCH_PacketNotifyLock();
         s.entityID = target.getEntityId();
         W_Network.sendToServer(s);
      }
   }

   /**
    * Server -> Client: notify a specific player about a lock event *for a specific aircraft entity id*.
    * Preferred overload to ensure the client can verify whether they're actually in the targeted aircraft.
    */
   public static void sendToPlayer(EntityPlayer player, int targetEntityId) {
      if (player instanceof EntityPlayerMP) {
         MCH_PacketNotifyLock s = new MCH_PacketNotifyLock();
         s.entityID = targetEntityId;
         W_Network.sendToPlayer(s, (EntityPlayerMP) player);
      }
   }

   /**
    * Server -> Client: legacy helper kept for compatibility.
    * We attempt to infer the aircraft the player is riding/controlling.
    * If none is found, entityID stays -1 and the client will ignore it.
    */
   public static void sendToPlayer(EntityPlayer player) {
      if (player instanceof EntityPlayerMP) {
         int targetId = -1;

         // Try to resolve the aircraft this player is riding/controlling
         MCH_EntityAircraft ac = MCH_EntityAircraft.getAircraft_RiddenOrControl(player);
         if (ac != null) targetId = ac.getEntityId();

         MCH_PacketNotifyLock s = new MCH_PacketNotifyLock();
         s.entityID = targetId;
         W_Network.sendToPlayer(s, (EntityPlayerMP) player);
      }
   }
}
