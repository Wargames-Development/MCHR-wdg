package mcheli.aircraft;

import mcheli.MCH_MOD;
import mcheli.MCH_PacketNotifyLock;
import mcheli.weapon.MCH_EntityBaseBullet;
import mcheli.wrapper.W_Lib;
import mcheli.wrapper.W_McClient;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import java.util.List;

public class MCH_MissileDetector {

    public static final int SEARCH_RANGE = 60;
    private MCH_EntityAircraft ac;
    private World world;
    private int alertCount;


    public MCH_MissileDetector(MCH_EntityAircraft aircraft, World w) {
        this.world = w;
        this.ac = aircraft;
        this.alertCount = 0;
    }

    public void update() {
        if (!this.ac.haveFlare()) return;

        if (this.alertCount > 0) {
            --this.alertCount;
        }

        // One-tick flags written elsewhere
        boolean trackingNow = this.ac.getEntityData().getBoolean("Tracking");
        if (trackingNow) {
            this.ac.getEntityData().setBoolean("Tracking", false);
        }

        // If someone explicitly set "LockOn", emit a packet burst (server -> seated players)
        if (this.ac.getEntityData().getBoolean("LockOn")) {
            if (this.alertCount == 0) {
                this.alertCount = 10;

                if (!this.world.isRemote && this.ac != null && this.ac.haveFlare() && !this.ac.isDestroyed()) {
                    int seats = this.ac.getSeatNum(); // seat ids 0..seats (0 pilot)
                    for (int seatId = 0; seatId <= seats; ++seatId) {
                        Entity seated = this.ac.getEntityBySeatId(seatId);
                        if (seated instanceof EntityPlayerMP) {
                            MCH_PacketNotifyLock s = new MCH_PacketNotifyLock();
                            s.entityID = this.ac.getEntityId(); // identify THIS aircraft
                            mcheli.wrapper.W_Network.sendToPlayer(s, (EntityPlayerMP) seated);
                        }
                    }
                }
            }
            // consume the flag
            this.ac.getEntityData().setBoolean("LockOn", false);
        }

        if (this.ac.isDestroyed()) return;

        // Pilot or first seat entity reference (used for client-local checks)
        Entity ridden = this.ac.getRiddenByEntity();
        if (ridden == null) {
            ridden = this.ac.getEntityBySeatId(1);
        }

        if (ridden == null) return;

        if (this.ac.isFlareUsing()) {
            this.destroyMissile();
            return;
        }

        // Periodic alert (lock maintained) — gate with alertCount to avoid spam
        boolean locked = trackingNow || this.isLockedByMissile();
        if (this.alertCount == 0 && locked && this.hasalert()) {
            this.alertCount = 20;

            if (!this.world.isRemote) {
                // SERVER: notify pilot + all occupied seats (no global sound)
                if (!this.ac.isUAV() && this.ac.haveFlare() && !this.ac.isDestroyed()) {
                    int seats = this.ac.getSeatNum();
                    for (int seatId = 0; seatId <= seats; ++seatId) {
                        Entity seated = this.ac.getEntityBySeatId(seatId);
                        if (seated instanceof EntityPlayerMP) {
                            MCH_PacketNotifyLock s = new MCH_PacketNotifyLock();
                            s.entityID = this.ac.getEntityId();
                            mcheli.wrapper.W_Network.sendToPlayer(s, (EntityPlayerMP) seated);
                        }
                    }
                }
                // For UAVs, the operator isn’t seated on the server entity; the client proxy will handle local tone.
            } else {
                // CLIENT: UAV case — play only for the actual local operator
                if (this.ac.isUAV() && this.hasalert()) {
                    // Only trigger for the local client controlling/associated with this UAV.
                    // Leave the rider check as a guard; proxy method is client-only under the hood.
                    if (mcheli.wrapper.W_Lib.isClientPlayer(ridden)) {
                        // Do NOT use global sound helpers here.
                        // Delegate to proxy so only the local operator hears it.
                        MCH_MOD.proxy.clientLocked();
                    }
                }
            }
        }
    }


    public void destroyMissile() {
        List list = this.world.getEntitiesWithinAABB(MCH_EntityBaseBullet.class, this.ac.boundingBox.expand(300.0D, 300.0D, 300.0D));
        if (list == null) {
            return;
        }
        for (Object o : list) {
            MCH_EntityBaseBullet msl = (MCH_EntityBaseBullet) o;
            if (msl.targetEntity != null && (this.ac.isMountedEntity(msl.targetEntity) || msl.targetEntity.equals(this.ac))) {
                if (msl.getInfo().isHeatSeekerMissile) {
                    if (msl.getInfo().antiFlareCount > 0) {
                        if (msl.antiFlareTick > msl.getInfo().antiFlareCount) {
                            msl.targetEntity = null;
                            msl.antiFlareTick = 0;
                        } else {
                            msl.antiFlareTick++;
                        }
                    } else {
                        msl.targetEntity = null;
                    }
                    //msl.setDead();
                } else {


                }
            }
        }

    }

    public boolean isLockedByMissile() {
        List list = this.world.getEntitiesWithinAABB(MCH_EntityBaseBullet.class, this.ac.boundingBox.expand(300.0D, 300.0D, 300.0D));
        if (list != null) {
            for (int i = 0; i < list.size(); ++i) {
                MCH_EntityBaseBullet msl = (MCH_EntityBaseBullet) list.get(i);
                if (msl.targetEntity != null && (this.ac.isMountedEntity(msl.targetEntity) || msl.targetEntity.equals(this.ac))) {
                    return true;
                }
            }
        }

        return false;
    }


    public boolean hasalert() {
        if (this.ac.hasalert()) {
            return true;
        }

        return false;
    }

}
