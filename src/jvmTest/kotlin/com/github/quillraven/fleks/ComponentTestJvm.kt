package com.github.quillraven.fleks

import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertFailsWith

internal class ComponentTestJvm {

    private data class ComponentTestComponent(var x: Int = 0)

    private val componentFactory =
        mapOf<KClass<*>, () -> Any>(ComponentTestComponent::class to { ComponentTestComponent() })

    @Test
    fun cannotRemoveNonExistingEntityFromMapperWithInsufficientCapacity() {
        val cmpService = ComponentService(componentFactory)
        val mapper = cmpService.mapper<ComponentTestComponent>()
        val entity = Entity(10_000)

        assertFailsWith<IndexOutOfBoundsException> { mapper.removeInternal(entity) }
    }
}
