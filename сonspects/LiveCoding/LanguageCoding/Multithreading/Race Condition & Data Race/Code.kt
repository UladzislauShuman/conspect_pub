/**
 * Race Condition -- когда результат зависит от того, какой поток Прибежал первым
 * базовый пример -- Read-Modify-Write (C++)
 * 
 * Data Race -- два потока одновременно обращаются к перменной. один из них на запись
 * 
 * как выявить
 * - стресс-тесты
 * - инструменты для статического анализа
 * - недетерменированный результат
 * 
 * как решить
 * - взаимное исключение
 * - Lock-free (CAS)
 * - Ввыполнение всех операций записи в одном выделенном потоке
 *
 */

// проблема

class CashbackService {
    var totalCashback = 0
    val limit = 1000

    fun addBonus() {
        totalCashback++ // read, modify, write
    }

    fun addBonusWithLimit() {
        if (totalCashback < limit) { // тут может быть другой поток
            totalCashback++
        }
    }
}

// решения

class SynchronizedCashbackService {
    private var total = 0 
    private val lock = Any()

    fun process (ids: List<Int>) {
        val executor = Executors.newFixedThreadPool(8)

        ids.forEach {
            executor.submit {
                synchronized(lock) {
                    total++
                }
            }
        }
        executor.shutdown()
    }
}

class AtomicCashback {
    private val total = AtomicInteger(0)

    fun process(ids: List<Int>) {
        val futures = ids.map {
            CompletableFuture.runAsync {
                total.incrementAndGet()
            }
        }
        CompletableFuture.allOf(*futures.toTypedArray()).join()
    }
}

class CoroutineMutexCashback {
    private val total = 0
    private val mutex = Mutex()

    suspend fun process(ids: List<Int>) = coroutineScope {
        ids.forEach {
            launch(Dispatchers.Default) {
                mutex.withLock { // приостанавливаем корутину
                    total++
                }
            }
        }
    }
}

class ConfinementCashback {
    private var total = 0
    private val counterContext = newSingleThreadContext("Thread") 

    suspend fun process(ids: List<Int>) = coroutineScope {
        ids.forEach {
            launch(Dispatchers.Default) {
                withContext(counterContext) {
                    total++
                }
            }
        }
    }
}