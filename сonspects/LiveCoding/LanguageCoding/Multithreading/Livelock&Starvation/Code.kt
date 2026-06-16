/**
 * Livelock -- это ситуация, когда потоки постоянно меняют свое состояние в ответ на действие друг друга, но никто не может завершить работу
 * аналогия с двумя человеками в узком коридоре
 * 
 * Starvation (Голодание) -- когда поток не может получить доступ к ресурсу, так как другие Жадные потоки постоянно его у него забирают
 * 
 * Как выявить
 * - высокая нагрузка на CPU без полезного результата
 * - из логов (например: бесконечные циклы Попробовал, Отменил, Попробовал)
 * 
 * Алгоритм решения
 * - Добавление случайной задержки перед повторной попыткой
 * - Увеличение времени ожидания с каждой неудачной попыткой
 * - Использовать очереди и локков, что отдают больше всех ожидающему
 */

/**
 * два сервиса, что пытаются захватить общую транзакцию. если один видит, что другой тоже хочет её, он отпускает её и пробует снова чуть позже
 */

class Transaction(var owner: String? = null)

fun processLivelock(name: String, otherName: String, tx: Transaction) {
    var isDone = false
    while (!isDone) {
        tx.owner = name
        println("$name took it")

        Thread.sleep(10)

        if (tx.owner != name) { // кто-то перехватил
            println("from $name tp $otherName")
            continue
        }

        isDone = true
    }
}

// решение

// 1
class Transaction(val lock: ReentrantLock(true))

fun process(name: String, tx: Transaction) {
    val executor = Executors.newSingleThreadExecutor()

    executor.submit {
        tx.lock.lock()
        try {
            println("$name took it")
            Thread.sleep(10)
            println("$name finished")
        } finally {
            tx.lock.unlock()
        }
    }
}

// 2
fun process(name: String, other: String, tx: Transaction, attempt: Int = 1) {
    CompletableFuture.runAsync {
        tx.owner = name
        Thread.sleep(10)

        if (tx.owner != name) {
            println("$name to $other , $attempt")

            val backoffDelay = 10L * Math.pow(2.0m aattempt.toDouble()).toLong()

            CompletableFuture.delayedExecutor(backoffDelay, TimeUnit.MILLISECONDS).execute {
                process(name, other, tx, attempt + 1)
            }
        } else {
            println("$name finished")
        }
    }
}

// 3
suspend fun process(name: String, other: String, tx: Transaction) {
    var isDone = false

    while (!isDone) {
        tx.owner = name 
        delay(10)

        if (tx.owner != name) {
            val jutter = Random.nextLong(10, 100)
            println("from $name to $other. wait $jutter")
            delay(jitter)
            continue
        }

        println("$name finished")
        isDone = true
    } 
}