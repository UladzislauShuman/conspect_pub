import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Обработать список из 10 транзакций. вызвать внешний сервис Платежный шлюз
 * - шлюз медленный
 * - может кинуть ошибку
 * - если одна транзакция упала с ошибкой, то остальные должны дойти до конца
 */

/** 
 * 3 способа, как это сделать
 */

data class Result(val id: Long, val status: String)

/**
 * ExecutorService
 */
fun processExecutor(ids: List<Long>): List<Result> {
    val executor = Executors.newFixedThreadPool(4)
    val futures = mutableList<Future<Result>>()

    for (id in ids) {
        val future = executor.submit<Result> {
            try {
                if (id == 3L)
                    throw RuntimeException("имитируем")
                Thread.sleep(300)
                Result(id, "SUCCESS")   
            } catch (e: Exception) {
                Result(id, "FAILED: ${e.message}")
            }
        }
        futures.add(future)
    }

    val results = futures.map { it.get() } // блокирует

    executor.shutdown()
    return results
}

/**
 * CompletableFuture
 */

import java.util.concurrent.CompletableFuture

fun processCompletableFuture(ids: List<Long>): List<Result> {
    val futures = ids.map { id -> 
        CompletableFuture.supplyAsync {
            if (id == 3L)
                    throw RuntimeException("имитируем")
                Thread.sleep(300)
                Result(id, "SUCCESS")  
        }.handle { result, exception ->
            if (ex != null) {
                Result(id, "FAILED: ${e.message}")
            } else {
                result
            }
        }
    }

    CompletableFuture.allOf(*futures.toTypeArray()).join()

    return futures.map {
        it.get()
    }
}

/**
 * Корутины
 */

suspend fun processCoroutines(ids: List<Long>): List<Result> = coroutinesScope {
    val deferreds = ids.map { id ->
        async(Dispatchers.IO) {
            try {
                if (id == 3L) 
                    throw RuntimeException("Network Timeout")
                delay(200) // не блокирует
                Result(id, "SUCCESS")
            } catch (e: Exception) {
                Result(id, "FAILED: ${e.message}")
            }
        }
    }

    deferreds.awaitAll()
}