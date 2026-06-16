/**
 * Visibility -- у каждого ядра есть свой кеш, и он не всем ядрам виден. на этой Невидиомсти изменений и строятся проблемы
 * Reordering -- JIT компилятор, в процессе оптимизации, может переставить местами операции для того, чтобы оптимизировать выполнение. 
 * 
 * Проявление
 * - процесс завис
 * - невозможнные состояния и тп
 * 
 * решение
 * - volatile 
 * - happens before через монитор 
 * - проверка статуса задачи
 */

 // проблема


class PaymentProcessor : Thread() {
    var stopRequested = false

    override fun run() {
        println("Start")
        while (!stopRequested) {
            // ...
        }
        println("finish")
    }
}

fun main() {
    val processor = PaymentProcessor()
    processor.start()

    Thread.sleep(1000)

    println("main")

    processor.stopRequested = true // другой поток может и не увидеть
}


// решение


// 1
class VolatilePaymentProcessor : Thread() {
    @Volatile
    var stopRequested = false

    override fun run() {
        println("Start")
        while (!stopRequested) {
            // ...
        }
        println("finish")
    }
}

// 2
class AtomicPaymentProcessor {
    private val isRunning = AtomicBoolean(true)

    fun startProcessing() {
        val executor = Executors.newSingleThreadExecutor()
        executor.submit {
            while (isRunning.get()) {

            }
            println("Stop")
        }
    }

    fun stop() {
        isRunning.set(false)
    }
}

// 3

fun main() = runBlocking {
    val job = launch(Dispatchers.Default) {
        println("Start")

        while (isActive) { // это встроенный флаг

        }   
        println("Stop")
    }

    delay(1000)
    println("main")
    job.cancelAndJoin() // отменяет корутину и ждет ее завершения
} 

