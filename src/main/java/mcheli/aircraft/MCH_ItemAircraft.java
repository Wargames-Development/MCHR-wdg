package mcheli.aircraft;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import mcheli.MCH_Achievement;
import mcheli.MCH_Config;
import mcheli.MCH_MOD;
import mcheli.wrapper.W_EntityPlayer;
import mcheli.wrapper.W_Item;
import mcheli.wrapper.W_MovingObjectPosition;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDispenser;
import net.minecraft.block.BlockSponge;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityMinecartEmpty;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.world.World;
import mcheli.weapon.MCH_WeaponSet;


public abstract class MCH_ItemAircraft extends W_Item {

   private static boolean isRegistedDispenseBehavior = false;

   // Placement state tags & timing
   private static final String TAG_START_TICK   = "StartTick";    // long: world time when we pinned the target
   private static final String TAG_READY_UNTIL  = "ReadyUntil";   // long: server grace window end
   private static final String TAG_CANCEL_UNTIL = "CancelUntil";  // long: brief server cooldown after cancel
   private static final String TAG_READY_SHOWN  = "ReadyShown";   // boolean: client showed “Release…” once
   private static final String TAG_LAST_REPIN_MSG = "LastRepinMsg"; // long world time

   private static final int READY_GRACE_TICKS     = 20;  // ~1s
   private static final int CANCEL_COOLDOWN_TICKS = 10;  // ~0.5s


   // --- client-only transient state (do NOT persist to NBT) ---
   private static final java.util.Set<Integer> CLIENT_READY_HINT_SHOWN = new java.util.HashSet<Integer>();
   private static final java.util.Map<Integer, Long> CLIENT_COOLDOWN_UNTIL = new java.util.HashMap<Integer, Long>();
   private static final java.util.Map<Integer, Long> CLIENT_LAST_START_TICK = new java.util.HashMap<Integer, Long>();



   private static int normalizeSnowY(World w, int x, int y, int z) {
      Block b = w.getBlock(x, y, z);
      return (b == Blocks.snow_layer) ? (y - 1) : y;
   }

   public MCH_ItemAircraft(int i) {
      super(i);
   }



   public static void registerDispenseBehavior(Item item) {
      if(!isRegistedDispenseBehavior) {
         BlockDispenser.dispenseBehaviorRegistry.putObject(item, new MCH_ItemAircraftDispenseBehavior());
      }
   }

   //@Override
   //public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean par4) {
   //   MCH_AircraftInfo info = MCH_AircraftInfoManager.getFromItem(stack.getItem());
   //   if (info != null && !"zzz".equals(info.category)) {
   //      lines.add(EnumChatFormatting.RED + "DANGER!");
   //      lines.add(EnumChatFormatting.RED + "This vehicle is not in the default category!");
   //      lines.add(EnumChatFormatting.RED + "May contain experimental features!");
   //   }
//
   //   super.addInformation(stack, player, lines, par4);
   //}

   public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean par4) {
      MCH_AircraftInfo info = this.getAircraftInfo().category.equals("zzz") ? null : this.getAircraftInfo();
      MCH_EntityAircraft ac = createAircraft(player.worldObj, -1.0D, -1.0D, -1.0D, stack);
      if (info != null) {
         lines.add(EnumChatFormatting.YELLOW + "Category: " + info.category);
         //lines.add(EnumChatFormatting.DARK_PURPLE + "Weapon: " + info.weaponSetList);
         //         tooltip.add(TextFormatting.DARK_PURPLE + "Weapons: " + Arrays.stream(ac.weapons).map(MCH_WeaponSet::getName).collect(Collectors.joining(", ")));
         lines.add(EnumChatFormatting.DARK_PURPLE + "Weapons: " + Arrays.stream(ac.weapons).map(MCH_WeaponSet::getName).collect(Collectors.joining(", ")));
         //im sure this will work
      }

      if (ac != null &&
              ac.isNewUAV()) {
         lines.add(EnumChatFormatting.RED + "DANGER!");
         lines.add(EnumChatFormatting.RED + "This drone has a new UAV mechanic!");
         lines.add(EnumChatFormatting.RED + "It may contain a lot of bugs!");
         lines.add(EnumChatFormatting.RED + "Clear your inventory before use!");
      }
//
      super.addInformation(stack, player, lines, par4);
   }

   public abstract MCH_AircraftInfo getAircraftInfo();

   public abstract MCH_EntityAircraft createAircraft(World var1, double var2, double var4, double var6, ItemStack var8);

   MCH_EntityAircraft ac;
   //todo add a wait time for the aircraft to be placed, we dont want people abusing vehicle hopping
   public MCH_EntityAircraft onTileClick(ItemStack itemStack, World world, float rotationYaw, int x, int y, int z) {

      MCH_EntityAircraft ac = this.createAircraft(world, (double)((float)x + 0.5F), (double)((float)y + 1.0F), (double)((float)z + 0.5F), itemStack);
      if(ac == null) {
         return null;
      } else {
         //hopefully reloads the 'aircraft' (vehicle)'s textures when placed.
         //if(ac.getAcInfo() != null) {
         //   ac.getAcInfo().reload();
         //   ac.changeType(ac.getAcInfo().name);
         //   ac.onAcInfoReloaded();
         //}
         //causes a crash when the fucking model is not loaded
         ac.initRotationYaw((float)(((MathHelper.floor_double((double)(rotationYaw * 4.0F / 360.0F) + 0.5D) & 3) - 1) * 90));
         return !world.getCollidingBoundingBoxes(ac, ac.boundingBox.expand(-0.1D, -0.1D, -0.1D)).isEmpty()?null:ac;
      }
   }

   public String toString() {
      MCH_AircraftInfo info = this.getAircraftInfo();
      return info != null?super.toString() + "(" + info.getDirectoryName() + ":" + info.name + ")":super.toString() + "(null)";
   }

   @Override
   public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
      float f = 1.0F;
      float f1 = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * f;
      float f2 = player.prevRotationYaw   + (player.rotationYaw   - player.prevRotationYaw)   * f;
      double d0 = player.prevPosX + (player.posX - player.prevPosX) * f;
      double d1 = player.prevPosY + (player.posY - player.prevPosY) * f + 1.62D - player.yOffset;
      double d2 = player.prevPosZ + (player.posZ - player.prevPosZ) * f;
      Vec3 eye = W_WorldFunc.getWorldVec3(world, d0, d1, d2);

      float c = MathHelper.cos(-f2 * 0.017453292F - (float)Math.PI);
      float s = MathHelper.sin(-f2 * 0.017453292F - (float)Math.PI);
      float cp = -MathHelper.cos(-f1 * 0.017453292F);
      float sp =  MathHelper.sin(-f1 * 0.017453292F);
      double dist = 5.0D;

      Vec3 look  = Vec3.createVectorHelper(s * cp, sp, c * cp);
      Vec3 reach = eye.addVector(look.xCoord * dist, look.yCoord * dist, look.zCoord * dist);

      MovingObjectPosition mop = W_WorldFunc.clip(world, eye, reach, true);
      if (mop == null) return stack;

      // Don't start if an entity is blocking immediately in front
      boolean blockingEntity = false;
      List list = world.getEntitiesWithinAABBExcludingEntity(
              player,
              player.boundingBox.addCoord(look.xCoord * dist, look.yCoord * dist, look.zCoord * dist).expand(1.0, 1.0, 1.0)
      );
      for (Object o : list) {
         Entity e = (Entity)o;
         if (e.canBeCollidedWith()) {
            float border = e.getCollisionBorderSize();
            if (e.boundingBox.expand(border, border, border).isVecInside(eye)) { blockingEntity = true; break; }
         }
      }
      if (blockingEntity) return stack;

      if (W_MovingObjectPosition.isHitTypeTile(mop)) {
         if (MCH_MOD.config.PlaceableOnSpongeOnly.prmBool) {
            Block b = world.getBlock(mop.blockX, mop.blockY, mop.blockZ);
            if (!(b instanceof BlockSponge)) return stack;
         }
         if (world.getWorldTime() < 100) return stack;

         if (stack.stackTagCompound == null) stack.stackTagCompound = new NBTTagCompound();
         NBTTagCompound tag = stack.stackTagCompound;

         if (!tag.hasKey("TargetX")) {
            // First press this attempt: pin target & start server stopwatch
            int tx = mop.blockX;
            int ty = normalizeSnowY(world, mop.blockX, mop.blockY, mop.blockZ);
            int tz = mop.blockZ;

            tag.setInteger("TargetX", tx);
            tag.setInteger("TargetY", ty);
            tag.setInteger("TargetZ", tz);

            tag.setLong(TAG_START_TICK, world.getTotalWorldTime());
            tag.removeTag(TAG_READY_UNTIL);
            tag.removeTag(TAG_CANCEL_UNTIL);

            // reset client-only hint/cooldown state (prevents duplicates)
            if (world.isRemote) {
               int pid = player.getEntityId();
               CLIENT_READY_HINT_SHOWN.remove(pid);
               CLIENT_COOLDOWN_UNTIL.remove(pid);
            }

            if (world.isRemote) {
               player.addChatMessage(new ChatComponentText("Hold right-click to deploy…"));
            } else {
               System.out.println(" [DEBUG] " + player.getCommandSenderName()
                       + " is trying to place " + this.getUnlocalizedName().replace("item.", "")
                       + " at " + tx + "," + ty + "," + tz + ".");
            }
         }

         // Ensure we’re in “using” state; calling again while using is harmless
         if (!player.isUsingItem()) {
            player.setItemInUse(stack, this.getMaxItemUseDuration(stack));
         }
      }
      return stack;
   }





   @Override
   public int getMaxItemUseDuration(ItemStack stack) {
      return 72000;
   }

   @Override
   public EnumAction getItemUseAction(ItemStack stack) {
      return EnumAction.bow;
   }

   @Override
   public void onUsingTick(ItemStack stack, EntityPlayer player, int count) {
      if (stack.stackTagCompound == null) return;
      NBTTagCompound tag = stack.getTagCompound();

      // ---------------- CLIENT ----------------
      if (player.worldObj.isRemote) {
         long now = player.worldObj.getTotalWorldTime();
         int pid = player.getEntityId();

         // (MINIMAL CHANGE) Do NOT obey server TAG_CANCEL_UNTIL on the client,
         // it could hide the "ready" hint at the exact threshold tick.
         // if (tag.hasKey(TAG_CANCEL_UNTIL) && now < tag.getLong(TAG_CANCEL_UNTIL)) return;

         // keep our own tiny client-side hush after a target mismatch
         if (CLIENT_COOLDOWN_UNTIL.containsKey(pid) && now < CLIENT_COOLDOWN_UNTIL.get(pid)) return;

         // reset the one-shot "ready" hint whenever the server restarts the attempt
         if (tag.hasKey(TAG_START_TICK)) {
            long startSeen = tag.getLong(TAG_START_TICK);
            Long prev = CLIENT_LAST_START_TICK.get(pid);
            if (prev == null || prev.longValue() != startSeen) {
               CLIENT_LAST_START_TICK.put(pid, startSeen);
               CLIENT_READY_HINT_SHOWN.remove(pid);
            }
         } else {
            CLIENT_LAST_START_TICK.remove(pid);
            CLIENT_READY_HINT_SHOWN.remove(pid);
         }

         // need a pinned target + start
         if (!tag.hasKey("TargetX") || !tag.hasKey("TargetY") || !tag.hasKey("TargetZ") || !tag.hasKey(TAG_START_TICK)) {
            return;
         }

         // suppress hint if not still looking at pinned block
         MovingObjectPosition mop = getBlockIncludingWater(player, 5.0D);
         if (mop == null) {
            CLIENT_COOLDOWN_UNTIL.put(pid, now + CANCEL_COOLDOWN_TICKS);
            CLIENT_READY_HINT_SHOWN.remove(pid);
            return;
         }
         int tx = tag.getInteger("TargetX");
         int ty = tag.getInteger("TargetY");
         int tz = tag.getInteger("TargetZ");
         int curX = mop.blockX;
         int curY = normalizeSnowY(player.worldObj, mop.blockX, mop.blockY, mop.blockZ);
         int curZ = mop.blockZ;

         if (curX != tx || curY != ty || curZ != tz) {
            // just hush the UI until server re-pins; no chat spam here
            CLIENT_COOLDOWN_UNTIL.put(pid, now + CANCEL_COOLDOWN_TICKS);
            CLIENT_READY_HINT_SHOWN.remove(pid);
            return;
         }

         // one-shot "Release..." exactly once per attempt
         long start = tag.getLong(TAG_START_TICK);
         long elapsed = now - start;
         if (elapsed >= MCH_Config.placetimer.prmInt && !CLIENT_READY_HINT_SHOWN.contains(pid)) {
            player.addChatMessage(new ChatComponentText("Release right-click to deploy."));
            CLIENT_READY_HINT_SHOWN.add(pid);
         }
         return;
      }

      // ---------------- SERVER ----------------
      long now = player.worldObj.getTotalWorldTime();

      // respect server cancel cooldown (don’t re-arm mid-cooldown)
      if (tag.hasKey(TAG_CANCEL_UNTIL) && now < tag.getLong(TAG_CANCEL_UNTIL)) return;

      // must have pinned target to validate
      if (!tag.hasKey("TargetX") || !tag.hasKey("TargetY") || !tag.hasKey("TargetZ")) {
         // no pinned target; nothing to do while holding
         return;
      }

      int tx = tag.getInteger("TargetX");
      int ty = tag.getInteger("TargetY");
      int tz = tag.getInteger("TargetZ");

      // current aim
      MovingObjectPosition mop = getBlockIncludingWater(player, 5.0D);
      if (mop == null) {
         // quiet cooldown; client UI will hush
         tag.setLong(TAG_CANCEL_UNTIL, now + CANCEL_COOLDOWN_TICKS);
         return;
      }
      int curX = mop.blockX;
      int curY = normalizeSnowY(player.worldObj, mop.blockX, mop.blockY, mop.blockZ);
      int curZ = mop.blockZ;

      // if target changed WHILE STILL HOLDING: seamlessly re-pin + restart timer
      if (curX != tx || curY != ty || curZ != tz) {
         tag.setInteger("TargetX", curX);
         tag.setInteger("TargetY", curY);
         tag.setInteger("TargetZ", curZ);
         tag.setLong(TAG_START_TICK, now);       // restart stopwatch
         tag.removeTag(TAG_READY_UNTIL);         // clear any grace from the old target
         tag.setLong(TAG_CANCEL_UNTIL, now + 2); // tiny hush to prevent flicker

         // OPTIONAL: gently inform the player (throttled to 1/sec)
         long last = tag.hasKey(TAG_LAST_REPIN_MSG) ? tag.getLong(TAG_LAST_REPIN_MSG) : 0L;
         if (now - last >= 20) {
            player.addChatMessage(new ChatComponentText("Target changed — restarting deployment…"));
            tag.setLong(TAG_LAST_REPIN_MSG, now);
         }
         return;
      }

      // surface must remain valid
      Block block = player.worldObj.getBlock(tx, ty, tz);
      if (!block.getMaterial().isSolid() && block.getMaterial() != Material.water) {
         tag.setLong(TAG_CANCEL_UNTIL, now + CANCEL_COOLDOWN_TICKS);
         return;
      }

      // start stopwatch if missing (edge cases)
      if (!tag.hasKey(TAG_START_TICK)) {
         tag.setLong(TAG_START_TICK, now);
      }

      long start = tag.getLong(TAG_START_TICK);
      long elapsed = now - start;

      // first time we reach timer, open a small grace window
      if (elapsed >= MCH_Config.placetimer.prmInt && !tag.hasKey(TAG_READY_UNTIL)) {
         tag.setLong(TAG_READY_UNTIL, now + READY_GRACE_TICKS);
      }
   }






   private void cancelDeployment(NBTTagCompound tag, EntityPlayer player, String message) {
      // Set cooldown; do not stop using, do not clear tags here
      long now = player.worldObj.getTotalWorldTime();
      tag.setLong(TAG_CANCEL_UNTIL, now + CANCEL_COOLDOWN_TICKS);
      tag.setBoolean(TAG_READY_SHOWN, false); // allow the hint again next attempt

      // Player-only feedback
      player.addChatMessage(new ChatComponentText(message));
   }


   private void clearDeployTags(NBTTagCompound tag) {
      tag.removeTag("DeployStart");
      tag.removeTag("TargetX");
      tag.removeTag("TargetY");
      tag.removeTag("TargetZ");
      tag.removeTag(TAG_START_TICK);
      tag.removeTag(TAG_READY_UNTIL);
      tag.removeTag(TAG_CANCEL_UNTIL);
      tag.removeTag("DeployMsgShown");
      tag.removeTag(TAG_READY_SHOWN);
      tag.removeTag(TAG_LAST_REPIN_MSG);
      // client-only maps are cleared on release in onPlayerStoppedUsing
   }



   public MovingObjectPosition getBlockIncludingWater(EntityPlayer player, double range) {

      //if !player.worldObj.isRemote)
      //why does this STUPID FUCKING SHIT FATALLY ERROR THE SERVER

      Vec3 eyePos = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
      Vec3 lookVec = player.getLookVec();
      Vec3 targetPos = eyePos.addVector(lookVec.xCoord * range, lookVec.yCoord * range, lookVec.zCoord * range);

      MovingObjectPosition mop = player.worldObj.rayTraceBlocks(eyePos, targetPos);

      if (mop == null) {
         // fallback: get block at position player is looking at ignoring collision
         int checkX = (int)Math.floor(targetPos.xCoord);
         int checkY = (int)Math.floor(targetPos.yCoord);
         int checkZ = (int)Math.floor(targetPos.zCoord);

         Block block = player.worldObj.getBlock(checkX, checkY, checkZ);
         if (block.getMaterial() == Material.water || block.getMaterial().isSolid()) {
            return new MovingObjectPosition(checkX, checkY, checkZ, 0, targetPos);
         }
      }

      return mop;
   }

   //@Override
   //public void onPlayerStoppedUsing(ItemStack stack, World world, EntityPlayer player, int timeLeft) {
   //   if (stack.stackTagCompound != null && stack.stackTagCompound.hasKey("DeployStart")) {
   //      cancelDeployment(stack.stackTagCompound, player, "Vehicle deployment cancelled (input released).");
   //   }
   //}

   @Override
   public void onPlayerStoppedUsing(ItemStack stack, World world, EntityPlayer player, int timeLeft) {
      if (stack.stackTagCompound == null) return;
      NBTTagCompound tag = stack.getTagCompound();

      if (!world.isRemote) {
         long now = world.getTotalWorldTime();

         // if cancel cooldown active, this is a cancel
         if (tag.hasKey(TAG_CANCEL_UNTIL) && now < tag.getLong(TAG_CANCEL_UNTIL)) {
            clearDeployTags(tag);
            player.addChatMessage(new ChatComponentText("Vehicle deployment cancelled."));
            return;
         }

         // must have a target + start time
         if (!tag.hasKey("TargetX") || !tag.hasKey("TargetY") || !tag.hasKey("TargetZ") || !tag.hasKey(TAG_START_TICK)) {
            clearDeployTags(tag);
            player.addChatMessage(new ChatComponentText("Vehicle deployment cancelled."));
            return;
         }

         int tx = tag.getInteger("TargetX");
         int ty = tag.getInteger("TargetY");
         int tz = tag.getInteger("TargetZ");
         long start = tag.getLong(TAG_START_TICK);
         long elapsed = now - start;

         boolean timerOK = elapsed >= MCH_Config.placetimer.prmInt;
         boolean graceOK = tag.hasKey(TAG_READY_UNTIL) && now <= tag.getLong(TAG_READY_UNTIL);

         if (timerOK || graceOK) {
            ty = normalizeSnowY(world, tx, ty, tz);
            Block b = world.getBlock(tx, ty, tz);
            if (b.getMaterial().isSolid() || b.getMaterial() == Material.water) {
               spawnAircraft(stack, world, player, tx, ty, tz);
               W_WorldFunc.MOD_playSoundAtEntity(player, "deploy", 1.0F, 1.0F);
               clearDeployTags(tag);
               player.addChatMessage(new ChatComponentText("Vehicle deployed."));
               System.out.println(" [DEBUG] " + player.getCommandSenderName()
                       + " has placed " + this.getUnlocalizedName().replace("item.", "")
                       + " at " + tx + "," + ty + "," + tz + ".");
               return;
            }
         }

         // Not placed → clean up
         clearDeployTags(tag);
         player.addChatMessage(new ChatComponentText("Vehicle deployment cancelled."));
      } else {
         // tidy client-only transient flags on release
         int pid = player.getEntityId();
         CLIENT_READY_HINT_SHOWN.remove(pid);
         CLIENT_COOLDOWN_UNTIL.remove(pid);
      }
   }






   //@Override
   //public boolean canContinueUsing(ItemStack stack, World world, EntityLivingBase entity, int count) {
   //   return true;
   //}
   //idk if we will still need this but it is here

   //@Override
   //public boolean canContinueUsing(ItemStack oldStack, ItemStack newStack) {
   //   // Return true only if StartCount tag is still present (meaning still holding)
   //   NBTTagCompound tag = oldStack.getTagCompound();
   //   return tag != null && tag.hasKey("StartCount");
   //}
   //CANT FUCKING DO THAT BECAUSE THIS MOD WAS CODED BY LITERAL MONKIES AND USES A WRAPPER!!!





   public MCH_EntityAircraft spawnAircraft(ItemStack itemStack, World world, EntityPlayer player, int x, int y, int z) {


      MCH_EntityAircraft ac = this.onTileClick(itemStack, world, player.rotationYaw, x, y, z);
      if(ac != null) {
         if(ac.isUAV() || ac.isNewUAV()) {
            if(world.isRemote) {
               if(ac.isSmallUAV()) {
                  W_EntityPlayer.addChatMessage(player, "Please use the UAV station OR Portable Controller");
               } else {
                  W_EntityPlayer.addChatMessage(player, "Please use the UAV station");
               }
            }

            ac = null;
         } else {
            if(!world.isRemote) {
               ac.getAcDataFromItem(itemStack);
               world.spawnEntityInWorld(ac);
               MCH_Achievement.addStat(player, MCH_Achievement.welcome, 1);
            }

            if(!player.capabilities.isCreativeMode) {
               --itemStack.stackSize;
            }
         }
      }

      return ac;
   }

   public void rideEntity(ItemStack item, Entity target, EntityPlayer player) {
      MCH_Config var10000 = MCH_MOD.config;
      if(!MCH_Config.PlaceableOnSpongeOnly.prmBool && target instanceof EntityMinecartEmpty && target.riddenByEntity == null) {
         MCH_EntityAircraft ac = this.spawnAircraft(item, player.worldObj, player, (int)target.posX, (int)target.posY + 2, (int)target.posZ);
         if(!player.worldObj.isRemote && ac != null) {
            ac.mountEntity(target);
         }
      }

   }

}
