import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.TimeUnit

/**
 * deadlock -- это ситуация, когда два+ потока бесконечно ждут освобождения ресурсов, которые заняты друг другом
 * 
 * условия возникновения
 * - взаимное исключение -- к примеру, ресурс может быть занят только одним потоком в момент времени
 * - поток удержимвает один ресурс И ждет доступа к другому
 * - нет принудительного вытеснения. только поток может отпустить монитор
 * - циклическое ожидание -- поток 1 поток 2, и поток 2 поток 1
 * 
 * алгоритмы решения
 * - упорядочивание ресурсов
 * - использовать tryLock с TimeOut
 * - глобальный lock
 * - stipe lock или concurrent hash map (вместо самого объекта -- некий бакет)
 */

class Account(val id: Long, var balance: Long) {
    val lock = ReentrantLock()
}

object GlobalLock

class TransferService {
    /**
     * deadlock
     */
    fun transfer(from: Account, to: Account, amount: Long) {
        synchronized(from) {
            println("${Thread.currentThread().name}: Заблокировал счет ${from.id}")
            Thread.sleep(50) // что-то с ним делаем

            synchronized(to) {
                println("${Thread.currentThread().name}: Заблокировал счет ${to.id}")
                if (from.balance >= amount) {
                    from.balance -= amount
                    to.balance += amount
                    println("Перевод $amount выполнен успешно")
                }
            }
        }
    }

    /**
     * упорядочивание
     */
    fun transferA(from: Account, to: Account, amount: Long) {
        if (from.id == to.id)
            return 

        val first = if(from.id < to.id) from else to
        val second = if (from.id < to.id) to else from

        synchronized(first) {
            synchronized(second) {
                if (from.balance >= amount) {
                    from.balance -= amount
                    to.balance += amount
                }
            }
        }
    }

    /**
     * timeout
     */
    fun transferB(from: Account, to: Account, amount: Long) {
        while(true) {
            val fromLockAcquired = from.lock.tryLock()
            val toLtoLockAcquiredock = to.lock.tryLock(50, TimeUntit.MILLISECONDS)

            try {
                if (fromLockAcquired && toLtoLockAcquiredock) {
                    if (from.balance >= amount) {
                        from.balance -= amount
                        to.balance += amount
                    }
                    return
                }
            } finally {
                if (fromLockAcquired) from.lock.unlock()
                if (toLockAcquired) to.lock.unlock()
            }

            Thread.sleep(kotlin.random.Random.nextLong(10, 50))
        }
    }

    /**
     * глобальный замок
     */
    fun transferB(from: Account, to: Account, amount: Long) {
        synchronized(GlobalLock) {
            if (from.balance >= amount) {
                from.balance -= amount
                to.balance += amount
            }
        }
    }

    /**
     * можно по hash-у упорядочить 
     * если они равны -- то третий лок
     */
    private val tieLock = Any()

    fun transferC(from: Account, to: Account, amount: Long) {
        val fromHash = System.identityHashCode(from)
        val toHash = System.identityHashCode(to)

        if (fromHash < toHash) {
            synchronized(from) {
                synchronized(to) {
                    if (from.balance >= amount) {
                        from.balance -= amount
                        to.balance += amount
                    }
                }
            }
        } else if (toHash < fromHash) {
            synchronized(to) {
                synchronized(from) {
                    if (from.balance >= amount) {
                        from.balance -= amount
                        to.balance += amount
                    }
                }
            }
        } else {
            synchronized(tieLock) {
                synchronized(from) { 
                    synchronized(to) { 
                        if (from.balance >= amount) {
                            from.balance -= amount
                            to.balance += amount
                        }
                    } 
                }
            }
        }
    }

}

fun main() {
    val service = TransferService()
    val acc1 = Account(1, 1000)
    val acc2 = Acoount(2, 1000)

    val t1 = Thread({ service.transfer(acc1, acc2, 100) }, "Thread-1")
    val t2 = Thread({ service.transfer(acc2, acc1, 50) }, "Thread-2")

    t1.start()
    t2.start()
}