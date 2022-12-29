package joehot200.AntiAuraESP.esp.util;


import java.lang.reflect.InvocationTargetException;
import java.util.*;

import static com.comphenix.protocol.PacketType.Play.Server.*;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.FieldAccessException;
import joehot200.AntiAuraESP.AntiAuraESP;
import org.bukkit.Bukkit;
import org.bukkit.entity.Boat;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.Plugin;


import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

public class EntityHider implements Listener {
	protected Table<Integer, Integer, Boolean> observerEntityMap = HashBasedTable.create();

	// Packets that update remote player entities
	private static PacketType[] ENTITY_PACKETS;
	private static PacketType[] ENTITY_MOVEMENTONLY;
	private static Set<PacketType> SPAWN_PACKETS;
	private ProtocolManager manager;

	// Listeners
	private Listener bukkitListener;
	private PacketAdapter protocolListener;

	public EntityHider(Plugin plugin) {
		SPAWN_PACKETS = new HashSet<PacketType>();
		SPAWN_PACKETS.add(NAMED_ENTITY_SPAWN);
		SPAWN_PACKETS.add(SPAWN_ENTITY);
		if (SPAWN_ENTITY_LIVING.isSupported()) {
			SPAWN_PACKETS.add(SPAWN_ENTITY_LIVING);
			SPAWN_PACKETS.add(SPAWN_ENTITY_PAINTING);
		}
		SPAWN_PACKETS.add(SPAWN_ENTITY_EXPERIENCE_ORB);
		// <1.14 packets
		PacketType[] ENTITY_PACKETS;// We don't handle DESTROY_ENTITY though
		if (BED.isSupported()) {
			ENTITY_PACKETS = new PacketType[]{ENTITY_EQUIPMENT, BED, ANIMATION, NAMED_ENTITY_SPAWN, COLLECT, SPAWN_ENTITY,
					SPAWN_ENTITY_LIVING, SPAWN_ENTITY_PAINTING, SPAWN_ENTITY_EXPERIENCE_ORB, ENTITY_VELOCITY,
					REL_ENTITY_MOVE, ENTITY_LOOK, ENTITY_MOVE_LOOK, ENTITY_MOVE_LOOK, ENTITY_TELEPORT,
					ENTITY_HEAD_ROTATION, ENTITY_STATUS, ATTACH_ENTITY, ENTITY_METADATA, ENTITY_EFFECT,
					REMOVE_ENTITY_EFFECT, BLOCK_BREAK_ANIMATION
					// We don't handle DESTROY_ENTITY though
			};
		}
		// >=1.14 packets
		else {

			if (SPAWN_ENTITY_LIVING.isSupported()) {
				SPAWN_PACKETS.add(SPAWN_ENTITY_LIVING);
				SPAWN_PACKETS.add(SPAWN_ENTITY_PAINTING);
				ENTITY_PACKETS = new PacketType[]{ENTITY_EQUIPMENT, ANIMATION, NAMED_ENTITY_SPAWN, COLLECT, SPAWN_ENTITY,
						SPAWN_ENTITY_LIVING, SPAWN_ENTITY_PAINTING, SPAWN_ENTITY_EXPERIENCE_ORB, ENTITY_VELOCITY,
						REL_ENTITY_MOVE, ENTITY_LOOK, ENTITY_MOVE_LOOK, ENTITY_MOVE_LOOK, ENTITY_TELEPORT,
						ENTITY_HEAD_ROTATION, ENTITY_STATUS, ATTACH_ENTITY, ENTITY_METADATA, ENTITY_EFFECT,
						REMOVE_ENTITY_EFFECT, BLOCK_BREAK_ANIMATION
				};
			} else {
				ENTITY_PACKETS = new PacketType[]{ENTITY_EQUIPMENT, ANIMATION, NAMED_ENTITY_SPAWN, COLLECT, SPAWN_ENTITY,
						SPAWN_ENTITY_EXPERIENCE_ORB, ENTITY_VELOCITY,
						REL_ENTITY_MOVE, ENTITY_LOOK, ENTITY_MOVE_LOOK, ENTITY_MOVE_LOOK, ENTITY_TELEPORT,
						ENTITY_HEAD_ROTATION, ENTITY_STATUS, ATTACH_ENTITY, ENTITY_METADATA, ENTITY_EFFECT,
						REMOVE_ENTITY_EFFECT, BLOCK_BREAK_ANIMATION
				};
				// We don't handle DESTROY_ENTITY though
			}
		}
		EntityHider.ENTITY_PACKETS = ENTITY_PACKETS;


		PacketType[] ENTITY_INFO;

		if (SPAWN_ENTITY_LIVING.isSupported()) {
			ENTITY_INFO = new PacketType[]{NAMED_ENTITY_SPAWN, SPAWN_ENTITY,
					SPAWN_ENTITY_LIVING, SPAWN_ENTITY_PAINTING, SPAWN_ENTITY_EXPERIENCE_ORB, ENTITY_VELOCITY,
					REL_ENTITY_MOVE, ENTITY_LOOK, ENTITY_MOVE_LOOK, ENTITY_MOVE_LOOK, ENTITY_TELEPORT,
					// We don't handle DESTROY_ENTITY though
			};
		}else{
			ENTITY_INFO = new PacketType[]{NAMED_ENTITY_SPAWN, SPAWN_ENTITY,
					SPAWN_ENTITY_EXPERIENCE_ORB, ENTITY_VELOCITY,
					REL_ENTITY_MOVE, ENTITY_LOOK, ENTITY_MOVE_LOOK, ENTITY_MOVE_LOOK, ENTITY_TELEPORT,
					// We don't handle DESTROY_ENTITY though
			};
		}
		EntityHider.ENTITY_MOVEMENTONLY = ENTITY_INFO;
		Preconditions.checkNotNull(plugin, "plugin cannot be NULL.");

		this.manager = ProtocolLibrary.getProtocolManager();

		// Register events and packet listener
		plugin.getServer().getPluginManager().registerEvents(bukkitListener = constructBukkit(), plugin);
		manager.addPacketListener(protocolListener = constructProtocol(plugin));

	}


	public boolean setVisibility(Player observer, int entityID, boolean visible) {
			// Non-membership means they are visible
			return !setMembership(observer, entityID, !visible);
	}

	/**
	 * Add or remove the given entity and observer entry from the table.
	 * 
	 * @param observer - the player observer.
	 * @param entityID - ID of the entity.
	 * @param member   - TRUE if they should be present in the table, FALSE
	 *                 otherwise.
	 * @return TRUE if they already were present, FALSE otherwise.
	 */
	// Helper method
	public boolean setMembership(Player observer, int entityID, boolean member) {
		if (member) {
			return observerEntityMap.put(observer.getEntityId(), entityID, true) != null;
		} else {
			return observerEntityMap.remove(observer.getEntityId(), entityID) != null;
		}
	}

	/**
	 * Determine if the given entity and observer is present in the table.
	 * 
	 * @param observer - the player observer.
	 * @param entityID - ID of the entity.
	 * @return TRUE if they are present, FALSE otherwise.
	 */
	public boolean getMembership(Player observer, int entityID) {
		try {
			return observerEntityMap.contains(observer.getEntityId(), entityID);
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Determine if a given entity is visible for a particular observer.
	 * 
	 * @param observer - the observer player.
	 * @param entityID - ID of the entity that we are testing for visibility.
	 * @return TRUE if the entity is visible, FALSE otherwise.
	 */
	protected boolean isVisible(Player observer, int entityID) {
		// If we are using a whitelist, presence means visibility - if not, the opposite
		// is the case
		boolean presence = getMembership(observer, entityID);

		return !presence;
	}

	/**
	 * Remove the given entity from the underlying 
	 * 
	 * @param entity    - the entity to remove.
	 * @param destroyed - TRUE if the entity was killed, FALSE if it is merely
	 *                  unloading.
	 */
	protected void removeEntity(Entity entity, boolean destroyed) {
		try {
			int entityID = entity.getEntityId();

			for (Map<Integer, Boolean> maps : observerEntityMap.rowMap().values()) {
				maps.remove(entityID);
			}
		} catch (Exception e) {

		}
	}

	/**
	 * Invoked when a player logs out.
	 * 
	 * @param player - the player that jused logged out.
	 */
	protected void removePlayer(Player player) {
		// Cleanup
		observerEntityMap.rowMap().remove(player.getEntityId());
		// areCreatures.remove(player);
	}

	/**
	 * Construct the Bukkit event listener.
	 * 
	 * @return Our listener.
	 */
	private Listener constructBukkit() {
		return new Listener() {
			@EventHandler
			public void onEntityDeath(EntityDeathEvent e) {
				removeEntity(e.getEntity(), true);
			}

			@EventHandler
			public void onChunkUnload(ChunkUnloadEvent e) {
				for (Entity entity : e.getChunk().getEntities()) {
					removeEntity(entity, false);
				}
			}
		};



	}

	boolean fakeTPPacket = false;

	private PacketAdapter constructProtocol(Plugin plugin) {
		return new PacketAdapter(plugin, ENTITY_MOVEMENTONLY) {
			@Override
			public void onPacketSending(PacketEvent event) {
				// if (!TaskHandler.m.checkConfig.getBoolean("Enabled", true))
				// return;
				try {
					
					int entityID = event.getPacket().getIntegers().read(0);
					// See if this packet should be cancelled
					Player pl = event.getPlayer();
					if (!isVisible(pl, entityID) && entityID != pl.getEntityId() && !fakeTPPacket) {
						if (SPAWN_PACKETS.contains(event.getPacket().getType())){
							PacketContainer pa = event.getPacket();
							int num = 20;
							int doub = num * 2;
							double num0 = pa.getDoubles().read(0);
							double num2 = pa.getDoubles().read(2);
							pa.getDoubles().write(0, num0 - (num + (Math.random() * doub)));
							pa.getDoubles().write(2, num2 - (num + (Math.random() * doub)));
							pa.getDoubles().write(1, minheight);
						}else {
							event.setCancelled(true);
						}

					}
				} catch (FieldAccessException e) {
					// Occurs when trying to hide an NPC.
				}
			}
		};
	}
	
	double minheight = -300d;

	/**
	 * Toggle the visibility status of an entity for a player.
	 * <p>
	 * If the entity is visible, it will be hidden. If it is hidden, it will become
	 * visible.
	 * 
	 * @param observer - the player observer.
	 * @param entity   - the entity to toggle.
	 * @return TRUE if the entity was visible before, FALSE otherwise.
	 */
	public final boolean toggleEntity(Player observer, Entity entity) {
		if (isVisible(observer, entity.getEntityId())) {
			return hideEntity(observer, entity);
		} else {
			return !showEntity(observer, entity);
		}
	}

	/**
	 * Allow the observer to see an entity that was previously hidden.
	 * 
	 * @param observer - the observer.
	 * @param entity   - the entity to show.
	 * @return TRUE if the entity was hidden before, FALSE otherwise.
	 */
	boolean teleportEntity = false;
	boolean test = false;

	public final boolean showEntity(Player observer, Entity entity) {
		try {
			validate(observer, entity);
			boolean hiddenBefore = !setVisibility(observer, entity.getEntityId(), true);

			// observer.sendMessage("Shown " + entity.getType() + " Was hidden? " +
			// hiddenBefore);
			// Resend packets
			if (manager != null && hiddenBefore) {
				tpEntityRand(entity, observer, 2);
				if (teleportEntity) {
					PacketContainer destroyEntity = new PacketContainer(ENTITY_TELEPORT);
					// destroyEntity.getIntegers().write(0, new Integer[] { entity.getEntityId() });
					// destroyEntity.getIntegers().write(0, 1);

					destroyEntity.getIntegers().write(0, entity.getEntityId());
					destroyEntity.getDoubles().write(0, (double) entity.getLocation().getX())
							.write(1, entity.getLocation().getY()).write(2, (double) entity.getLocation().getZ());
					try {
						manager.sendServerPacket(observer, destroyEntity);
					} catch (InvocationTargetException e) {
						throw new RuntimeException("Cannot send server packet.", e);
					}

				}
				if (!teleportEntity) {
					
					List<Player> thing = Arrays.asList(observer);
					try {
					manager.updateEntity(entity, thing);
					}catch (IllegalArgumentException ex) {
						
					}
					// observer.sendMessage("Updated ");
					int i = 0;
					{//for (int i = 1; i <= 3; i++) {
						Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(AntiAuraESP.instance,
								new Runnable() {

									@Override
									public void run() {
										tpEntityRand(entity, observer, 2);
										try {
										manager.updateEntity(entity, thing);
										}catch (IllegalArgumentException ex) {
											
										}
									}
								}, i);
					}
				}
			}

			return hiddenBefore;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public void tpEntityRand(final Entity entity, Player observer, int wait) {
		if (!teleportEntity) return;
		Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(AntiAuraESP.instance, new Runnable() {

			@Override
			public void run() {

				PacketContainer destroyEntity = new PacketContainer(ENTITY_TELEPORT);
				// destroyEntity.getIntegers().write(0, new Integer[] { entity.getEntityId() });
				// destroyEntity.getIntegers().write(0, 1);
				
				destroyEntity.getIntegers().write(0, entity.getEntityId());
				if (isVisible(observer, entity.getEntityId())) {
					destroyEntity.getDoubles().write(0, (double) entity.getLocation().getX())
							.write(1, (double) entity.getLocation().getY())
							.write(2, (double) entity.getLocation().getZ());
				} else {
					int num = 20;
					int doub = num * 2;
					destroyEntity.getDoubles()
							.write(0, (double) entity.getLocation().getX() - (num + (Math.random() * doub))).write(1, minheight)
							.write(2, (double) entity.getLocation().getZ() - (num + (Math.random() * doub)));
				}
				fakeTPPacket = true;
				try {
					manager.sendServerPacket(observer, destroyEntity);
				} catch (InvocationTargetException e) {
					throw new RuntimeException("Cannot send server packet.", e);
				}
				fakeTPPacket = false;
				
			}
		}, wait);
	}

	/**
	 * Prevent the observer from seeing a given entity.
	 * 
	 * @param observer - the player observer.
	 * @param entity   - the entity to hide.
	 * @return TRUE if the entity was previously visible, FALSE otherwise.
	 */
	public final boolean hideEntity(Player observer, Entity entity) {
		try {
			if (entity instanceof Player) {
				Player p = (Player) entity;
				if (p.getAddress() == null) {
					return true;
				}
			}
			if (entity instanceof Boat) {
				return true;
			}
			if (!(entity instanceof LivingEntity)) {
				//Not living entity
				if (!(entity instanceof Vehicle)) {
					//Not a minecart either, so dont hide it
						return true;
				}
			}else {
				//If configured, dont hide huge entities
				if (entity instanceof Ghast || entity instanceof EnderDragon) {
					if (true/*AConfig.getConfig().config.getBoolean("ExemptHugeEntities", true)*/) {
						return true;
					}
				}
			}
			//if (AConfig.getConfig().config.getBoolean("HideOnlyPlayers", false) && !(entity instanceof Player)) return true;
			validate(observer, entity);
			boolean visibleBefore = setVisibility(observer, entity.getEntityId(), false);

			if (visibleBefore) { // If test we move the entity not destroy
				if (test) {
					tpEntityRand(entity, observer, 0);
				} else {
					PacketContainer destroyEntity = new PacketContainer(ENTITY_DESTROY);
					// destroyEntity.getIntegers().write(0, new Integer[] { entity.getEntityId() });
					// destroyEntity.getIntegers().write(0, 1);
					try {
						destroyEntity.getIntegerArrays().write(0, new int[] { entity.getEntityId() });
					} catch (Exception ex) {
						test = false;
						teleportEntity = true;
						tpEntityRand(entity, observer, 0);
						if (true) return visibleBefore;
						List<Integer> entityIDList = new ArrayList<Integer>();
						entityIDList.add(entity.getEntityId());
						destroyEntity.getIntLists().write(0, entityIDList);
					}
					if (!test) {
						// Make the entity disappear
						try {
							manager.sendServerPacket(observer, destroyEntity);
						} catch (InvocationTargetException e) {
							throw new RuntimeException("Cannot send server packet.", e);
						}
						Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(AntiAuraESP.instance,
								new Runnable() {

									@Override
									public void run() {
										// Make the entity disappear
										try {
											manager.sendServerPacket(observer, destroyEntity);
										} catch (InvocationTargetException e) {
											throw new RuntimeException("Cannot send server packet.", e);
										}

									}
								}, 1);
					}
				}
			}
			return visibleBefore;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

	}

	/**
	 * Determine if the given entity has been hidden from an observer.
	 * <p>
	 * Note that the entity may very well be occluded or out of range from the
	 * perspective of the observer. This method simply checks if an entity has been
	 * completely hidden for that observer.
	 * 
	 * @param observer - the observer.
	 * @param entity   - the entity that may be hidden.
	 * @return TRUE if the player may see the entity, FALSE if the entity has been
	 *         hidden.
	 */
	public final boolean canSee(Player observer, Entity entity) {
		validate(observer, entity);

		return isVisible(observer, entity.getEntityId());
	}

	// For valdiating the input parameters
	private void validate(Player observer, Entity entity) {
		Preconditions.checkNotNull(observer, "observer cannot be NULL.");
		Preconditions.checkNotNull(entity, "entity cannot be NULL.");
	}


	public void close() {
		if (manager != null) {
			HandlerList.unregisterAll(bukkitListener);
			manager.removePacketListener(protocolListener);
			manager = null;
		}
	}
}