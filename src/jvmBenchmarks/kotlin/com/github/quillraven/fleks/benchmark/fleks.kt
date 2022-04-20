package com.github.quillraven.fleks.benchmark

import com.github.quillraven.fleks.*
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

data class FleksPosition(var x: Float = 0f, var y: Float = 0f)

data class FleksLife(var life: Float = 0f)

data class FleksSprite(var path: String = "", var animationTime: Float = 0f)

class FleksSystemSimple(
    private val positions: ComponentMapper<FleksPosition>
) : IteratingSystem(
    family { allOf(FleksPosition::class) }
) {
    override fun onTickEntity(entity: Entity) {
        positions[entity].x++
    }
}

class FleksSystemComplex1(
    private val positions: ComponentMapper<FleksPosition>,
    private val lifes: ComponentMapper<FleksLife>,
    private val sprites: ComponentMapper<FleksSprite>,
) : IteratingSystem(
    family {
        allOf(FleksPosition::class)
        noneOf(FleksLife::class)
        anyOf(FleksSprite::class)
    }
) {
    private var actionCalls = 0

    override fun onTickEntity(entity: Entity) {
        if (actionCalls % 2 == 0) {
            positions[entity].x++
            configureEntity(entity) { lifes.add(it) }
        } else {
            configureEntity(entity) { positions.remove(it) }
        }
        sprites[entity].animationTime++
        ++actionCalls
    }
}

class FleksSystemComplex2(
    private val positions: ComponentMapper<FleksPosition>,
    private val lifes: ComponentMapper<FleksLife>,
) : IteratingSystem(
    family { anyOf(FleksPosition::class, FleksLife::class, FleksSprite::class) }
) {

    override fun onTickEntity(entity: Entity) {
        configureEntity(entity) {
            lifes.remove(it)
            positions.add(it)
        }
    }
}

@State(Scope.Benchmark)
open class FleksStateAddRemove {
    lateinit var world: World

    @Setup(value = Level.Iteration)
    fun setup() {
        world = World {
            entityCapacity = NUM_ENTITIES

            components {
                add(::FleksPosition)
            }
        }
    }
}

@State(Scope.Benchmark)
open class FleksStateSimple {
    lateinit var world: World

    @Setup(value = Level.Iteration)
    fun setup() {
        world = World {
            entityCapacity = NUM_ENTITIES

            components {
                add(::FleksPosition)
            }

            systems {
                add(FleksSystemSimple(mapper()))
            }
        }

        repeat(NUM_ENTITIES) {
            world.entity { add<FleksPosition>() }
        }
    }
}

@State(Scope.Benchmark)
open class FleksStateComplex {
    lateinit var world: World

    @Setup(value = Level.Iteration)
    fun setup() {
        world = World {
            entityCapacity = NUM_ENTITIES

            components {
                add(::FleksPosition)
                add(::FleksLife)
                add(::FleksSprite)
            }

            systems {
                add(FleksSystemComplex1(mapper(), mapper(), mapper()))
                add(FleksSystemComplex2(mapper(), mapper()))
            }
        }

        repeat(NUM_ENTITIES) {
            world.entity {
                add<FleksPosition>()
                add<FleksSprite>()
            }
        }
    }
}

@Fork(1)
@Warmup(iterations = WARMUPS)
@Measurement(iterations = ITERATIONS, time = TIME, timeUnit = TimeUnit.SECONDS)
open class FleksBenchmark {
    @Benchmark
    fun addRemove(state: FleksStateAddRemove) {
        repeat(NUM_ENTITIES) {
            state.world.entity { add<FleksPosition>() }
        }
        repeat(NUM_ENTITIES) {
            state.world.remove(Entity(it))
        }
    }

    @Benchmark
    fun simple(state: FleksStateSimple) {
        repeat(WORLD_UPDATES) {
            state.world.update(1f)
        }
    }

    @Benchmark
    fun complex(state: FleksStateComplex) {
        repeat(WORLD_UPDATES) {
            state.world.update(1f)
        }
    }
}
