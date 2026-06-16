import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * проблемы с инкрементом
 * и несколько путей ее решения
 */


// проблемный код
/**
 * проблема
 * - ++ не атомарна, это три операции в одной
 * - ArrayList не безопасен. два потока могут записать значение в одну и туже ячейку массива. может быть в конце ArrayIndexOutOfBoundsException
 * - ну и проблема видимости JMM
 */
class TransactionStatService {
    var totalCount = 0     // счетчик обработанных транзакций
    val processedIds = mutableListOf<Long>() // список всех обработанных ID

    fun processTransactions(ids: List<Long>) {
        val executor = Executors.newFixedThreadPool(8)

        for (id in ids) {
            executor.submit {
                // полезная работа
                Thread.sleep(1) 
                
                // увеличиваем
                totalCount++
                
                // добавляем
                processedIds.add(id)
            }
        }

        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.MINUTES)
        
        println("ожидалось: ${ids.size}")
        println("реальность: $totalCount")
        println("размер списка: ${processedIds.size}")
    }
}

/**
 * Решение 1
 * Блокирующая синхронизация
 */
class TransactionStatServiceA {
    var totalCount = 0
    val processedIds = mutableListOf<Long>()

    private val lock = Any()

    fun processTransaction(ids: List<Long>) {
        val executor = Executors.newFixedThreadPool(8)

        for (id in ids) {
            executor.submit {
                Thread.sleap(1)

                synchronized(lock) {
                    totalCount++
                    processedIds.add(id)
                }
            }
        }

        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.MINUTES)
    }
}

/**
 * Решение 2
 * Неблокирующая синхронизация
 */
class TransactionStatServiceA {
    var totalCount = AtomicInteger(0)
    val processedIds = ConcurentLinkedQuque<Long>()

    fun processTransaction(ids: List<Long>) {
        val executor = Executors.newFixedThreadPool(8)
        
        for (id in ids) {
            Threaed.sleep(1)

            totalCount.incrementAndGet()

            processedIds.add(id)
        }

        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.MINUTES)
    }
}

fun main() {
    val service = TransactionStatService()
    val data = (1L..10000L).toList()
    service.processTransactions(data)
}