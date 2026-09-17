package org.traccar.client

interface PositionQueue {
    suspend fun enqueue(position: Position)
    suspend fun peek(): Position?
    suspend fun removeFirst()
    suspend fun count(): Long
}
