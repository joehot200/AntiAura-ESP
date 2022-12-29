package joehot200.AntiAuraESP.esp;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import joehot200.AntiAuraESP.AntiAuraESP;
import joehot200.AntiAuraESP.esp.util.EntityHider;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.NumberConversions;
import org.bukkit.util.Vector;

public class EntityHidingA implements Listener {

	//This is where the REAL stuff happens.
	//Should we separate this into different classes? Maybe! Who cares! It's open source now, why don't YOU do it?


	//TODO: Once again, this contains some messy code from v1.
	//However, this is *not* 100% the same as v1, due to various upgrades.
	//For example, this version will cast two rays that meet in the middle, optimising underground entities.



	public HashMap<String, PlayerData> data = new HashMap<String, PlayerData>();
	public PlayerData getPlayerData(Player p){
		if (!data.containsKey(p.getName())){
			data.put(p.getName(), new PlayerData(p));
		}
		return data.get(p.getName());
	}
	public class PlayerData{

		Player p;
		public PlayerData(Player p){
			this.p = p;
		}


		public ConcurrentHashMap<Entity, MapEntity> nearbyEntities = new ConcurrentHashMap<Entity, MapEntity>();

		public Location footLoc = null;
		public Location headLoc = null;

	}

	public static EntityHidingA instance;
	public EntityHider hider;

	int distinConfig = 50;
	int distYinConfig = 20;

	public MapEntity createEntity(Player pl, LivingEntity en) {
		if (en instanceof Player) {
			Player p = (Player) en;
			if (p.getAddress() == null) { // An NPC
				return null;
			}
		}
		// Add new MapEntity for concurrent use.
		Location eyeLoc = ((LivingEntity) en).getEyeLocation();

		MapEntity ent = new MapEntity(en, en instanceof LivingEntity, en instanceof Player, en.getWorld(),
				en.getLocation(), eyeLoc, en.isDead(), isGlowing(en), en.getCustomName() != null, en.getType(), en.getEntityId());

		getPlayerData(pl).nearbyEntities.put(en, ent);
		return ent;
}

	public boolean isGlowing(LivingEntity en){
		try{
			return en.isGlowing();
		}catch (Exception | Error e){
			//This should not happen unless 1.8 or something.. and to hell with 1.8....
			//... So messy solution for now? *sigh*
			//Do I even want to support 1.8? If users are lazy enough to use 1.8 I am too lazy to make a better solution
			return false;
		}
	}

	public boolean USE_ANTIAURA_COMPATIBILITY = false;

	@SuppressWarnings("deprecation")

	public EntityHidingA(AntiAuraESP m) {
		if (Bukkit.getServer().getPluginManager().getPlugin("AntiAura") != null){
			Bukkit.getConsoleSender().sendMessage("[AntiAura-ESP] Successfully hooked into AntiAura!");
			USE_ANTIAURA_COMPATIBILITY = true;
			//16 * 4 = 64
			AntiAuraAPI.AAPI.getAntiAuraAPI().setAsyncSnapshotRange(4);
			Bukkit.getConsoleSender().sendMessage("[AntiAura-ESP] Performance will be improved.");
		}
		instance = this;
		hider = new EntityHider(m);
		
		final int delay = 12;//checkConfig.getInt("ChunkStoreInterval", 20);
		Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(m, new Runnable() {


			@Override
			public void run() {
				for (Player pl : Bukkit.getServer().getOnlinePlayers()) {

					PlayerData data = getPlayerData(pl);

					data.footLoc = (pl.getLocation());
					data.headLoc = (pl.getEyeLocation());
				}
			}
		}, 0, 1);
		Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(m, new Runnable() {

			@Override
			public void run() {

				// Grab all entities around player
				for (Player pl : Bukkit.getServer().getOnlinePlayers()) {

					PlayerData data = getPlayerData(pl);
					int dist = distinConfig;
					int distY = distYinConfig;
					List<Entity> ents = pl.getNearbyEntities(dist, distY, dist);

					// Create entity
					for (Entity en : ents) {
						if (!(en instanceof LivingEntity)) continue;
						LivingEntity ent = (LivingEntity) en;
						if (!data.nearbyEntities.containsKey(en)) {
							createEntity(pl, ent);
						} else {
							MapEntity existingEntity = data.nearbyEntities.get(en);
							if (!en.isValid())
								continue;
							if (existingEntity == null) {
								existingEntity = createEntity(pl, ent);
								data.nearbyEntities.put(ent, existingEntity);
							}
							existingEntity.setLoc(en.getLocation());
							existingEntity.setEyeLoc(((LivingEntity) en).getEyeLocation());
							existingEntity.isDead = en.isDead();
							existingEntity.isGlowing = isGlowing(ent);
							existingEntity.hasNametag = en.getCustomName() != null;
							existingEntity.world = en.getWorld();
							// Do nothing. Entity is already known to be nearby.
						}
					}
					// End of entity creation
					// Remove entities that are not nearby
					for (Entity ent : data.nearbyEntities.keySet()) {
						if (!ents.contains(ent)) {
							//hider.setMembership(pl, ent.getEntityId(), true);
							hider.hideEntity(pl, ent);
							data.nearbyEntities.remove(ent);
							continue;
						}
					}
					// wrap.nearbyEntities.clear();
					// wrap.nearbyEntities.addAll(ents);
				}
			}
		}, 0, 30);

		//So just an explanation of this. AntiAura (the premium plugin) uses fancy async chunk snapshots to get materials async.
		//This simply hooks into it if AntiAura is installed for better performance.

		if (USE_ANTIAURA_COMPATIBILITY) {
			Bukkit.getServer().getScheduler().scheduleAsyncRepeatingTask(m, new Runnable() {

				@Override
				public void run() {
					{
						// Hide entities async
						hideEntities(true);
						Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(m, new Runnable() {
							//But make them visible/invisible sync
							@Override
							public void run() {
								hideOrShow();

							}
						}, 0);
					}

				}
			}, 0, (long) 8);
		}else {
			Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(m, new Runnable() {

				@Override
				public void run() {
					 {
						// Hide entities sync
						hideEntities(false);
						hideOrShow();
					}

				}
			}, 0, (long) 8);
		}
	}

	public void hideOrShow(){
		for (Player pl : Bukkit.getServer().getOnlinePlayers()) {
			PlayerData data = getPlayerData(pl);
			for (Entity en : data.nearbyEntities.keySet()) {
				MapEntity mapEnt = data.nearbyEntities.get(en);
				if (mapEnt == null)
					continue;
				if (mapEnt.hasBeenChecked) {
					if (!mapEnt.canBeSeen) {
						hider.hideEntity(pl, en);
					} else {
						//pl.sendMessage("Showing Entity: " + en.getType());
						hider.showEntity(pl, en);
					}
				}
			}
		}
	}
	long currentMillsHide = 0;

	@SuppressWarnings("unused")
	public void hideEntities(boolean async) {
		// for (Player pl : Main.PlayerManager.onlinePlayers) { // TODO
		currentMillsHide = System.currentTimeMillis();
		for (PlayerData dat : data.values()) {
			if (!dat.p.isOnline()) {
				data.remove(dat.p.getName());
				continue;
			}
			// pl.sendMessage("Iterating");
			// PlayerWrapper wrap = PlayerWrapper.getPlayer(pl);
			// int dist = checkConfig.getInt("DistanceChecked");
			// int distY = checkConfig.getInt("DistanceCheckedY");
			for (MapEntity en : dat.nearbyEntities.values()) {


				if (en.world == dat.footLoc.getWorld() && !en.isDead) {
					// (pl.getLocation().distance(en.getLocation())
					{
						boolean lS = false;
						// pl.sendMessage("2");
						if (en.getLocation(true).distance(dat.headLoc) > 64) {
							// If distance > than reduced distance
							// Then hide the entity
							lS = false;
						} else if (dat.headLoc.distance(en.getLocation(false)) <= 3
								|| en.isGlowing || en.hasNametag) {
							lS = true;
						} else {

							{
								Location playerLoc = dat.headLoc;

								Location entityLoc = en.getLocation(false).clone().add(0, 1, 0);
								int div = 2;
								Location midPoint = playerLoc.toVector().add(entityLoc.toVector()).divide(new Vector(div, div, div)).toLocation(playerLoc.getWorld());

								lS = canSeeAs(dat.p, playerLoc, entityLoc,
										midPoint);


							}
							if (false /*checkConfig.getBoolean("HidePlayersBehind", false)*/) {
								// Assume Vector a = your eye position and b = other's eye position.
								final Location lo = dat.headLoc;
								Location o;
								if (en.isPlayer) {
									o = en.getLocation(true);
								} else {
									o = en.getLocation(false);
								}
								Vector c = lo.toVector().subtract(o.toVector()); // Get vector between you
								// and other
								Vector d = dat.headLoc.getDirection(); // Get direction you are
								// looking at
								double delta = c.dot(d);
								if (delta > 0) {
									// wrap.p.sendMessage("Hiding behind: " + en.entityType);
									lS = false;
								}
							}
						}
						if (dat.p.hasPotionEffect(PotionEffectType.BLINDNESS)
						) {
							if (dat.footLoc.distance(
									en.getLocation(false)) > (3.5 * 3.5)) {
								lS = false;
							}
						}
						if (lS) {
							// Make sure to do tracers and stuff
							en.canBeSeen = true;
							en.hasBeenChecked = true;

						} else {
							en.canBeSeen = false;
							en.hasBeenChecked = true;

						}
					}
				}
			}
		}
	}

		public int rayMode = 2;
		public int angleDiff = 10;

		// Below is ray tracing methods

		public boolean canSeeAs (Player pl, Location eyeLoc, Location o, Location midPoint){
			boolean canSeeFromPos = canSeeAsyncRa(pl, eyeLoc.clone(), o, midPoint);
			boolean canSeeFromFPos = false;
			if (false/*checkConfig.getBoolean("RaysFromF5Mode", true)*/) {
				// Get F5 head position
				Vector dir = pl.getEyeLocation().getDirection().multiply(-5.5);
				Location newLoc = eyeLoc.clone().add(dir);
				canSeeFromFPos = canSeeAsyncRa(pl, newLoc, o, midPoint);
				// pl.sendMessage("NewLoc: " + newLoc);
			}
			boolean canSeeFromUPos = false;
			if (false/*checkConfig.getBoolean("RaysFromAbove", true)*/) {
				// Get above head position
				Location newLoc = eyeLoc.clone().add(0, 1.5, 0);
				canSeeFromFPos = canSeeAsyncRa(pl, newLoc, o, midPoint);
				// pl.sendMessage("NewLoc: " + newLoc);
			}

			if (canSeeFromPos || canSeeFromFPos || canSeeFromUPos) {
				return true;
			}
			return false;
		}


		public boolean canSeeAsyncRa (Player pl, Location l, Location o, Location midPoint){
			if ((rayMode >= 5 && rayMode <= 7)) {
				boolean canSee = false;
				// Raymode 5 and above, use lots of rays
				Location lo = l.clone();
				// Location ol = o.clone().add(0.5, 0.5, 0.5);
				double range = 1;
				{
					switch (rayMode) {
						case 6:
							range = 2;
							break;
						case 7:
							range = 3;
							break;
					}
				}
				canSee = canSeeAsync(pl, l.clone(), o.clone(), midPoint);
				HashSet<Location> locs = new HashSet<>();
				double amt = 1.8;
				locs.add(lo.clone().add(amt, amt, amt));
				locs.add(lo.clone().add(-amt, -amt, -amt));

				locs.add(lo.clone().add(amt, amt, -amt));
				locs.add(lo.clone().add(-amt, -amt, amt));

				locs.add(lo.clone().add(-amt, amt, amt));
				locs.add(lo.clone().add(amt, -amt, -amt));
				for (Location loc : locs) {
					canSee = canSeeAsync(pl, loc, o.clone(), midPoint);
					if (canSee) break;
				}
				return canSee;
			} else {
				// Raymode <= 4, use 1 ray that checks blocks around
				return canSeeAsync(pl, l.clone(), o.clone(), midPoint);
			}
		}

		public boolean canSeeAsync (Player pl, Location l, Location o, Location midPoint){
			// pl.sendMessage("Ray Start: " + l.getX() + ", " + l.getY() + ", " + l.getZ());
			int leafCount = 0;
			if (!(l.getWorld() == o.getWorld()))
				return false;
			Vector dir = o.clone().subtract(l).toVector().normalize();
			Vector negativeDir = dir.clone().multiply(-1);
			double distance = l.distance(o);
			l.setDirection(dir);
			final Location fromPlayer = l.clone();

			final Location fromEntity = o.clone();

			boolean rayFromEntity = false;

			int limit = 0;
			while (true) {
				rayFromEntity = !rayFromEntity;
				limit++;
				if (limit > 500) {
					break;
				}
				Location lo;
				if (rayFromEntity) {
					lo = fromEntity;

					lo.add(negativeDir);
				} else {
					lo = fromPlayer;
					lo.add(dir);
				}
				if (lo.distanceSquared(midPoint) < 1.5) {
					// Check is near the player! He can see him!
					return true;
				}
				if (lo.distance(midPoint) > (distance * 0.5) + 2) {
					// Checks for blocks have gone past the player
					// In theory, this shouldn't happen. But just in case...
					return false;
				}

				boolean solid = false;
				// boolean wasNull = true;
				Material lastMat;
				Material b = null;
				Location loc = null;
				loc = lo.clone();
				b = asyncGetMatAt(pl, loc); // Note: Can return null
				if (b == null) {
					b = Material.AIR;
				}
				lastMat = b;
				if (isSolid(b, true) || leafCount >= 5) {
					if (b.toString().contains("LEAVES")) {
						leafCount++;
					}
					solid = true;
					Location getAround = getBlockLocation(loc).add(0.5, 0.5, 0.5);
					List<Location> locs = getNearbyLocations(getAround, 1);
					// List<Block> blocks = getNearbyBlocks(loc, 1);
					// if (Main.m.hCh.contains(pl.getName()))
					// pl.sendMessage("Solid! NearbyBlocks: " + locs.size());
					for (Location loca : locs) {

						//double degreesDiff = Math.toDegrees(dir.angle(loc.clone().subtract(loca).toVector().normalize())); // RADS

						/*
						 * if (degreesDiff > checkConfig.getDouble("MaxAngle", 114) || degreesDiff <
						 * checkConfig.getDouble("MinAngle", 105)) { // pl.sendMessage("DegreesDif: " +
						 * degreesDiff); // Do nothing! // Block is directly down the line so does not
						 * obstruct } else
						 */
						{
							Material mat = asyncGetMatAt(pl, loca);
							if (mat == null) {
								mat = Material.AIR;
							}
							lastMat = mat;
							if (!isSolid(mat, false)) {
								if (mat.toString().contains("LEAVES")) {
									leafCount++;
								}
								solid = false;
								break;
							}
						}
					}
				}
				//pl.sendMessage("is solid: " + lastMat);
				if (solid) {
					break;
				}


			}
			return false;
		}


		public Material asyncGetMatAt (Player p, Location lo){
			Location loc = lo.clone();


			if (USE_ANTIAURA_COMPATIBILITY){
				return AntiAuraAPI.AAPI.getAntiAuraAPI().getAsyncBlockAt(p, lo);
			}else{
				Block b = loc.getBlock();
				if (b != null) {
					return b.getType();
				}
			}

			return Material.AIR;
		}

		public int setBlockXZ ( int abs){
			int blockChunkX = abs % 16;

			if (blockChunkX < 0) {
				blockChunkX += 16;
			}
			return blockChunkX;
		}

		public static int castToChunk ( double i){

			return (int) NumberConversions.floor(i) >> 4;
			// return (int) Math.floor(i) >> 4;
			// return (int) Math.floor((double) i / (double) 16);
		}

		// Solid for sight rays?
		@SuppressWarnings("deprecation")
		boolean isSolid (Material mat,boolean leaves){
			boolean solid = true;
			String lowerCaseName = mat.name().toLowerCase();
			if (lowerCaseName.contains("lava")) {
				// Leave solid as true
			} else if (!mat.isSolid() || mat.isTransparent() || lowerCaseName.contains("glass")
					|| lowerCaseName.contains("water") || lowerCaseName.contains("fence") || lowerCaseName.contains("slab")
					|| lowerCaseName.contains("door") || lowerCaseName.contains("door") || lowerCaseName.contains("bar")
					|| lowerCaseName.contains("tall") || lowerCaseName.contains("long") || lowerCaseName.contains("carpet")
					|| lowerCaseName.contains("wall")
					|| (lowerCaseName.contains("grass") && !lowerCaseName.equals("grass_block")) // Long + Tall grasses, ignore
					// grass block
					|| lowerCaseName.contains("vine") || lowerCaseName.contains("air") || lowerCaseName.contains("rail")
					|| (leaves && lowerCaseName.contains("lea")) // leaves

			) { // Turn solid to false
				solid = false;
			}
			if (!mat.isOccluding()) { // Is not a full block that fully blocks vision
				solid = false;
			}
			return solid;
		}

		public List<Location> getNearbyLocations (Location location,int radius){
			List<Location> blocks = new ArrayList<Location>();
			switch (rayMode) {
				case 0:
					double amt = radius;
					for (double x = -amt; x <= amt; x += amt) {
						for (double y = -amt; y <= amt; y += amt) {
							for (double z = -amt; z <= amt; z += amt) {
								if (x != 0 || y != 0 || z != 0) {
									Location loc = location.clone().add(x, y, z);
									// if (b.getType() != Material.AIR)
									blocks.add(loc);
								}
							}
						}
					}
					break;
				case 1:
					blocks.add(location.clone().add(1, 0, 0));
					blocks.add(location.clone().add(0, 0, 1));
					blocks.add(location.clone().add(-1, 0, 0));
					blocks.add(location.clone().add(0, 0, 1));
					blocks.add(location.clone().add(0, 1, 0));
					blocks.add(location.clone().add(0, -1, 0));
					break;
				case 2: // Case 2 and
				case 3: // case 3
				case 4: // and case 4
				{
					boolean far = false; // Check further if raymode is 3
					boolean nfar = (rayMode == 3); // Check further if raymode is 3
					if ((rayMode == 4)) {
						far = true;
						nfar = true;
					}
					Location loc = location.clone();
					double cYaw1 = loc.getYaw();
					// double cPitch = loc.getPitch();
					double cYaw = cYaw1 + 90;
					double rot; // Set later to determine yaw rotation
					// 1.5 block up
					// blocks.add(location.clone().add(0, 1.5, 0));
					// 1 block up
					blocks.add(location.clone().add(0, 1, 0));
					// .5 blocks up
					// NumberConversions.floor(num)
					// blocks.add(location.clone().add(0, 0.5, 0));
					double mult = 1;
					// 100 degrees To the left by default
					rot = -(90 + angleDiff);
					blocks.add(loc.clone().add(Math.cos(Math.toRadians(cYaw - rot)) * mult, 0,
							Math.sin(Math.toRadians(cYaw - rot)) * mult));


					// 100 degrees To the right by default
					rot = +(90 + angleDiff);

					blocks.add(loc.clone().add(Math.cos(Math.toRadians(cYaw - rot)) * mult, 0,
							Math.sin(Math.toRadians(cYaw - rot)) * mult));


					// Cut out duplicate blocks
					List<Location> blockLocations = new ArrayList<Location>();
					for (Location loca : blocks) {
						Location blockLoc = getBlockLocation(loca);
						if (!blockLocations.contains(blockLoc)) {
							blockLocations.add(blockLoc);
						}
					}
					// In rayMode 2, return the block locations
					// This avoids duplicate block position returns
					return blockLocations;
				}
				case 5:
				case 6: {
					// We do not handle raymodes 5 & 6 here.
					break;
				}
				case 10: {
					Location loc = location.clone();
					double cYaw1 = loc.getYaw();
					// double cPitch = loc.getPitch();
					double cYaw = cYaw1 + 90;
					double rot; // Set later to determine yaw rotation
					// 1.5 block up
					// blocks.add(location.clone().add(0, 1.5, 0));
					// 1 block up
					// blocks.add(location.clone().add(0, 1, 0));
					// .5 blocks up
					// blocks.add(location.clone().add(0, 0.5, 0));
					double mult = 1;

					rot = -(90);
					blocks.add(loc.clone().add(Math.cos(Math.toRadians(cYaw - rot)) * mult, 0,
							Math.sin(Math.toRadians(cYaw - rot)) * mult));

					rot = +(90);
					blocks.add(loc.clone().add(Math.cos(Math.toRadians(cYaw - rot)) * mult, 0,
							Math.sin(Math.toRadians(cYaw - rot)) * mult));

					// Cut out duplicate blocks
					List<Location> blockLocations = new ArrayList<Location>();
					for (Location loca : blocks) {
						Location blockLoc = getBlockLocation(loca);
						if (!blockLocations.contains(blockLoc)) {
							blockLocations.add(blockLoc);
						}
					}
					// In rayMode 2, return the block locations
					// This avoids duplicate block position returns
					return blockLocations;
				}
				// break; - Not required as we returned blockLocations
			}

			return blocks;
		}

		public Location getBlockLocation (Location loc){
			return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
		}

		public Location addRot (Location loc,double cYaw){
			return loc.clone().add(Math.cos(Math.toRadians(cYaw)), 0, Math.sin(Math.toRadians(cYaw)));
		}



	// TODO
	public class MapEntity {

		public Entity originalEntity;
		public boolean isLivingEntity;
		public boolean isPlayer;
		public World world;
		private Location location;
		private Location eyeLocation;
		public boolean isDead;
		public boolean isGlowing;
		public EntityType entityType;
		// getEntityID, NOT getUUID.
		public int entityID;

		public boolean hasBeenChecked = false;
		public boolean canBeSeen = false;
		public boolean hasNametag = false;

		public MapEntity(Entity originalEntity, boolean isLiving, boolean isPlayer, World world, Location location,
				Location eyeLocation, boolean isDead, boolean isGlowing, boolean hasNametag, EntityType entityType, int entityID) {
			this.originalEntity = originalEntity;
			this.isLivingEntity = isLiving;
			this.isPlayer = isPlayer;
			this.world = world;
			this.location = location;
			this.eyeLocation = eyeLocation;
			this.isDead = isDead;
			this.isGlowing = isGlowing;
			this.entityType = entityType;
			this.entityID = entityID;
			this.hasNametag = hasNametag;
		}

		public Location getLocation(boolean eyeLoc) {
			if (eyeLoc)
				return eyeLocation.clone();
			return location.clone();
		}

		public void setLoc(Location loc) {
			location = loc;
		}

		public void setEyeLoc(Location loc) {
			eyeLocation = loc;
		}
	}

}
