package com.github.quillraven.fleks

import com.github.quillraven.fleks.collection.bag
import kotlin.reflect.KClass

/**
 * Wrapper class for injectables of the [WorldConfiguration].
 * It is used in the [SystemService] to find out any unused injectables.
 */
data class Injectable(val injObj: Any, var used: Boolean = false)

@DslMarker
annotation class ComponentCfgMarker

@ComponentCfgMarker
class ComponentConfiguration {
    @PublishedApi
    internal val mappers = mutableMapOf<KClass<*>, ComponentMapper<*>>()

    @PublishedApi
    internal val mappersBag = bag<ComponentMapper<*>>()

    /**
     * Adds the specified component and its [ComponentListener] to the [world][World]. The [ComponentListener] can be omitted.
     *
     * @param compFactory the constructor method for creating the component.
     * @param listenerFactory the constructor method for creating the component listener.
     * @throws [FleksComponentAlreadyAddedException] if the component was already added before.
     */
    inline fun <reified T : Any> add(
        noinline compFactory: () -> T,
        listener: ComponentListener<T>? = null
    ) {
        val compType = T::class

        if (compType in mappers) {
            throw FleksComponentAlreadyAddedException(compType)
        }
        val compMapper = ComponentMapper(id = mappers.size, factory = compFactory)
        mappers[compType] = compMapper
        mappersBag.add(compMapper)

        if (listener != null) {
            compMapper.addComponentListenerInternal(listener)
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> mapper(): ComponentMapper<T> {
        val mapper = mappers[T::class] ?: throw FleksNoSuchComponentException(T::class)
        return mapper as ComponentMapper<T>
    }
}

@DslMarker
annotation class SystemCfgMaker

@SystemCfgMaker
class SystemConfiguration {
    internal val systems = mutableListOf<IntervalSystem>()

    fun add(system: IntervalSystem) {
        if (system in systems) {
            throw FleksSystemAlreadyAddedException(system::class)
        }
        systems.add(system)
    }
}

/**
 * A configuration for an entity [world][World] to define the initial maximum entity capacity,
 * the systems of the [world][World] and the systems' dependencies to be injected.
 * Additionally, you can define [ComponentListener] to define custom logic when a specific component is
 * added or removed from an [entity][Entity].
 */
class WorldConfiguration {
    /**
     * Initial maximum entity capacity.
     * Will be used internally when a [world][World] is created to set the initial
     * size of some collections and to avoid slow resizing calls.
     */
    var entityCapacity = 512

    @PublishedApi
    internal val systemFactory = mutableMapOf<KClass<*>, () -> IntervalSystem>()

    @PublishedApi
    internal val injectables = mutableMapOf<String, Injectable>()

    @PublishedApi
    internal val compCfg = ComponentConfiguration()

    internal val systemCfg = SystemConfiguration()

    fun components(cfg: ComponentConfiguration.() -> Unit) = compCfg.run(cfg)

    fun systems(cfg: SystemConfiguration.() -> Unit) = systemCfg.run(cfg)

    inline fun <reified T : Any> mapper(): ComponentMapper<T> = compCfg.mapper()
}

/**
 * A world to handle [entities][Entity] and [systems][IntervalSystem].
 *
 * @param cfg the [configuration][WorldConfiguration] of the world containing the initial maximum entity capacity
 * and the [systems][IntervalSystem] to be processed.
 */
class World(
    cfg: WorldConfiguration.() -> Unit
) {
    /**
     * Returns the time that is passed to [update][World.update].
     * It represents the time in seconds between two frames.
     */
    var deltaTime = 0f
        private set

    @PublishedApi
    internal val systemService: SystemService

    @PublishedApi
    internal val componentService: ComponentService

    @PublishedApi
    internal val entityService: EntityService

    /**
     * Returns the amount of active entities.
     */
    val numEntities: Int
        get() = entityService.numEntities

    /**
     * Returns the maximum capacity of active entities.
     */
    val capacity: Int
        get() = entityService.capacity

    init {
        val worldCfg = WorldConfiguration().apply(cfg)
        componentService = ComponentService(worldCfg.compCfg.mappers, worldCfg.compCfg.mappersBag)
        entityService = EntityService(worldCfg.entityCapacity, componentService)
        systemService = SystemService(this, worldCfg.systemCfg.systems)
    }

    /**
     * Adds a new [entity][Entity] to the world using the given [configuration][EntityCreateCfg].
     */
    inline fun entity(configuration: EntityCreateCfg.(Entity) -> Unit = {}): Entity {
        return entityService.create(configuration)
    }

    /**
     * Removes the given [entity] from the world. The [entity] will be recycled and reused for
     * future calls to [World.entity].
     */
    fun remove(entity: Entity) {
        entityService.remove(entity)
    }

    /**
     * Removes all [entities][Entity] from the world. The entities will be recycled and reused for
     * future calls to [World.entity].
     */
    fun removeAll() {
        entityService.removeAll()
    }

    /**
     * Performs the given [action] on each active [entity][Entity].
     */
    fun forEach(action: (Entity) -> Unit) {
        entityService.forEach(action)
    }

    /**
     * Returns the specified [system][IntervalSystem] of the world.
     *
     * @throws [FleksNoSuchSystemException] if there is no such [system][IntervalSystem].
     */
    inline fun <reified T : IntervalSystem> system(): T {
        return systemService.system()
    }

    /**
     * Returns a [ComponentMapper] for the given type. If the mapper does not exist then it will be created.
     *
     * @throws [FleksNoSuchComponentException] if the component of the given type does not exist in the
     * world configuration.
     */
    inline fun <reified T : Any> mapper(): ComponentMapper<T> = componentService.mapper()

    /**
     * Updates all [enabled][IntervalSystem.enabled] [systems][IntervalSystem] of the world
     * using the given [deltaTime].
     */
    fun update(deltaTime: Float) {
        this.deltaTime = deltaTime
        systemService.update()
    }

    /**
     * Removes all [entities][Entity] of the world and calls the [onDispose][IntervalSystem.onDispose] function of each system.
     */
    fun dispose() {
        entityService.removeAll()
        systemService.dispose()
    }
}
