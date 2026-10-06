package com.asmr.player.util

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

enum class LocalFileOperation(val parallel: Boolean = false) {
    LIBRARY, DOWNLOAD(true), FINALIZE_DOWNLOAD, SUBTITLE(true), POLISH_SUBTITLE, DELETE, CHANGE_DIRECTORY
}

/** 进程内文件与库记录的共同入口；先取得使用权，再读取任务或目录状态。 */
class LocalFileOperationCoordinator {
    private val monitor = Any()
    private val active = mutableSetOf<Lease>()
    private val waiting = ArrayDeque<Request>()

    class Lease internal constructor(
        private val coordinator: LocalFileOperationCoordinator,
        internal val operation: LocalFileOperation,
        internal val resource: String?,
        internal val scope: LocalFileScope,
    ) : AutoCloseable {
        override fun close() = coordinator.release(this)
    }

    private class Request(val operation: LocalFileOperation, val resource: String?, val scope: LocalFileScope) {
        val result = CompletableDeferred<Lease>()
        var granted: Lease? = null
    }

    private class Scope(val coordinator: LocalFileOperationCoordinator, val lease: Lease) :
        AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Scope>
    }

    fun tryAcquire(
        operation: LocalFileOperation,
        resource: String? = null,
        scope: LocalFileScope = LocalFileScope.Global,
    ): Lease? = synchronized(monitor) {
        if (waiting.any { it.scope.overlaps(scope) } || !compatible(operation, resource, scope)) null
        else grant(operation, resource, scope)
    }

    private fun compatible(operation: LocalFileOperation, resource: String?, scope: LocalFileScope): Boolean =
        active.all { lease ->
            !lease.scope.overlaps(scope) ||
                operation == lease.operation && operation.parallel && (resource == null || resource != lease.resource)
        }

    private fun grant(operation: LocalFileOperation, resource: String?, scope: LocalFileScope): Lease =
        Lease(this, operation, resource, scope).also(active::add)

    private fun drain() {
        val blocked = mutableListOf<LocalFileScope>()
        val granted = mutableListOf<Pair<Request, Lease>>()
        val iterator = waiting.iterator()
        while (iterator.hasNext()) {
            val request = iterator.next()
            if (blocked.none { it.overlaps(request.scope) } && compatible(request.operation, request.resource, request.scope)) {
                iterator.remove()
                val lease = grant(request.operation, request.resource, request.scope)
                request.granted = lease
                granted += request to lease
            } else {
                blocked += request.scope
            }
        }
        granted.forEach { (request, lease) -> request.result.complete(lease) }
    }

    private fun release(lease: Lease) = synchronized(monitor) {
        if (active.remove(lease)) drain()
    }

    private suspend fun acquire(operation: LocalFileOperation, resource: String?, scope: LocalFileScope): Lease {
        val request = synchronized(monitor) {
            if (waiting.none { it.scope.overlaps(scope) } && compatible(operation, resource, scope)) return grant(operation, resource, scope)
            Request(operation, resource, scope).also(waiting::addLast)
        }
        return try {
            request.result.await()
        } catch (error: Throwable) {
            synchronized(monitor) {
                waiting.remove(request)
                request.granted?.let(active::remove)
                drain()
            }
            throw error
        }
    }

    suspend fun <T> withOperation(
        operation: LocalFileOperation,
        resource: String? = null,
        scope: LocalFileScope = LocalFileScope.Global,
        block: suspend () -> T,
    ): T {
        val inherited = currentCoroutineContext()[Scope]
        if (inherited?.coordinator === this && inherited.lease.operation == operation &&
            (inherited.lease.scope.global || inherited.lease.scope == scope)) return block()
        val lease = acquire(operation, resource, scope)
        try {
            return withContext(Scope(this, lease)) { block() }
        } finally {
            lease.close()
        }
    }

    companion object {
        // WorkManager、手动创建的字幕仓库与 Hilt 对象必须使用同一个实例。
        val shared = LocalFileOperationCoordinator()
        const val BUSY_MESSAGE = "本地文件任务进行中，请稍后重试"
    }
}
