# Atomic-классы

## что это такое в общем?
- классы, что помогяют выполнить операцию над переменной атомарно и потокобезопасно без блокировок
- вспоминаем такие вещи как Compare-and-swap, ABA-проблема
- нужны, чтобы не навешивать и не избыточно блокировать

важно
- они не заменяют полностью synchronized
- не всегда быстрее 

## какие классы есть

java.util.concurrent.atomic
│
├── 🔢 СКАЛЯРНЫЕ (примитивы)
│   ├── AtomicInteger
│   ├── AtomicLong
│   └── AtomicBoolean
│
├── 🔗 ССЫЛОЧНЫЕ
│   └── AtomicReference\<T>
│
├── 🏷️ ВЕРСИОНИРОВАННЫЕ ССЫЛКИ (защита от ABA)
│   ├── AtomicStampedReference\<T>
│   └── AtomicMarkableReference\<T>
│
├── 📦 МАССИВЫ
│   ├── AtomicIntegerArray
│   ├── AtomicLongArray
│   └── AtomicReferenceArray\<T>
│
├── 🎯 FIELD UPDATER'ы (атомарный доступ к полю чужого объекта)
│   ├── AtomicIntegerFieldUpdater\<T>
│   ├── AtomicLongFieldUpdater\<T>
│   └── AtomicReferenceFieldUpdater\<T,V>
│
└── ⚡ АККУМУЛЯТОРЫ (Java 8+, высокая нагрузка)
    ├── LongAdder
    ├── LongAccumulator
    ├── DoubleAdder
    └── DoubleAccumulator

подробнее
- скалярные
    - счетчки актинвых соединений, запросов, генерация ID
    - нет AtomicFloat, Double. если надо, есть Guava
    - нет short, byte, char 
- ссылочные
    - сама ссылка становиться атомарной. содержимое по ссылке -- нет
    - но уязвим в ABA
- версионирование
    - AtomicStampedReference\<T> -- ссылка и счетчик
    - AtomicMarkableReference\<T> -- ссылка и флаг
- массивы
    - это обычный массив, где операция над каждым элементом атомарна
    - не путать с каким нибудь Array\<AtomicInteger>
    - это эканомит тем самым память, ибо -- меньше объектов-оберток
- field updater
    - атомарный доступ к полю чужого объекта
    - то есть -- можно менять volatile int поле в объекте типа T
    - зачем? -- использоваьт volatile без этой обертки в другом классе. эканомно, когда объектов много
    - пример
    ```java
    class Counter {
        @Volatile
        var value: Int = 0
        
        companion object {
            val UPDATER = AtomicIntegerFieldUpdater.newUpdater(
                Counter::class.java, "value"
            )
        }
    }

    val c = Counter()
    Counter.UPDATER.incrementAndGet(c) // атомарно
    ```
- аккумуляторы
    - цель -- быстрый счетчик при множестве потоков на запись
    - идея -- вместо одной переменной -- массив ячеек, каждый поток в свою пишет. в итоге при чтении вызывается sum()
    - метрики, счетчики где пишут часто, читают редко
    - плохо, когда редко пишут редко И если нужно читать значение и принимать решение


VarHandle -- как универсиальный механизм атомарного доступа

## почему CAS может быть медленно?

когда очень много потоков делает CAS, то они могут часто не получать результат, потому что другой поток уже сделал изменение. из-за чего постоянно промахиваться и повторять попытку.

## еще раз Lock-free
это алгоритм, который гарантирует прогресс системы в целом без использования блокировок: даже если какие-то потоки застревают, то хоть один идет дальше

важно отличать
- lock-free -- система всегда продолжает работать, но некоторые потоки могут задержаться
- wait-free -- каждый поток гарантированно завершает операцию за конечное число шагов
- obstruction-free -- поток прогрессирует, если работает один

## как кокунренция мешает

есть AtomicInteger И 100 потоков

100 потоков делают операцию ++

тогда
- поток А менаяет counter
- значение counter инвалидируется
- все потоки заново тащат значения из памяти
- это называется cach line bouncing
- поэтому под нагрузкой CAS-loop много промахивается

решение -- LongAdder, который, как мы помним, является массивом ячеек

но
- быстрая запись
- медленное чтение
- больше памяти

## по подробнее о некоторых классах

- Атомики-примитивы
    - кроме базовых, есть lazySet -- если ты пишешь значение, которое не нужно читать другим потокам прямо сейчас
    - compareAndSet(expected, newValue) -- это не CAS, это просто типо "если expected, то newValue"
    - weakCompareAndSet -- чето странное, ладно. типо -- может вернуть false, даже если expected совпадало
    - еще есть прикольно 
        - getAndIncrement
        - incrementAndGet
        - аналогично --
        - addAndGet и наоборот
    - объединили с лямбдами
    ```kotlin
    counter.accumulateAndGet(5) { current, x -> current + x * 2 }
    ```
    - это CAS-loop с моей лямбдой
    - и важно -- эта операция Может выполнться несколько раз, поэтому -- если добавить логирование, то оно может повторяться
    - когда хорошо
        - счетчик
        - генерация ID
        - rate-limiter
        - накопление статистики 
        - флаг "уже запушено/инициализировано"
        - graceful shutdown
            - когда сервис должен завершить работу, но не сразу
        - идемпотенция инициализации
            - это когда инициализация должна быть выполнена только один раз
    - когда плохо
        - когда нужна сложаня критическая секция из нескольких операций
        ```kotlin
        // ❌ две атомарные операции вместе НЕ атомарны
        if (counter.get() < limit) {
            counter.incrementAndGet()  // между if и increment кто-то мог влезть!
        }

        // ✅ одна атомарная операция через CAS-loop
        counter.updateAndGet { current ->
            if (current < limit) current + 1 else current
        }
        // или Mutex/synchronized 
        ```
        - экстремально высокая запись
        - атомарно изменить несколько полей
            - ну мы это снова возвращаемся к тому, что -- две атомарные операции вместе не атомарны

    - прикольно
        - AtomicReference(42) -- просто запакуется в Integer и получиться фигня какая-то
        - get не гарантирует того, что значение не изменится
        ```kotlin
        // Race condition
        if (counter.get() == 0) {
            // могло что-то случиться
            counter.set(10)
        }

        // Безопасно
        counter.compareAndSet(0, 10)
        ```
        - для своего рода "одного раза"
        ```kotlin
        // выполнить ровно один раз
        val initialized = AtomicBoolean(false)

        fun init() {
            if (initialized.compareAndSet(false, true)) {
                // здесь будет ровно один поток
            }
        }
        ```
        - toString() -- показыает значнеие в момент вызова. может поменяться

- atomic-reference
    - ну это по сути тоже самое, да. но только когда меняем состояние Целеком
    - то есть -- речь об immutable
    - есть такие же по идее операции
    - примеры когда
        - замена сложного состояния
        - ну там и Sate Machine недалеко
        - snapshot. copy-on-write (каждая запись ведет за собой новый объект)
        - lazy инициализация. но могут создаться два объекта и сохраниться только один. если создание дорогое -- лучше не это использовать
        - обернуть два поля и атомарно их менять как сложный объект
    - когда нет
        - не immutable
        - когда объект тяжелый 
        - если можно использовать volatile, когда просто читаешь и пишешь. не меняешь
    - важно
        - сравниваются ссылки, а не поля
        - напомним -- лямбда несколько раз может выполняться
        - желательно не nullable использовать, а optional
        - типы должны быть immutable


- atomic-stamped-reference и atomic-markable-reference
    - о решении проблемы ABA
    - на JVM эта проблема встречается редко в силу того, что GC обычно не дает переспользовать память сразу, но -- все равно может быть
    - AtomicStampedReference\<T>
        - ссылка + int-версия
        - в compareAndSwap нужно задавать по две пары expected-new
        - пример
        ```kotlin
        class LockFreeStack<T> {
            private data class Node<T>(val value: T, val next: Node<T>?)
            
            private val top = AtomicStampedReference<Node<T>?>(null, 0)
            
            fun push(value: T) {
                val stampHolder = IntArray(1)
                while (true) {
                    val currentTop = top.get(stampHolder)
                    val currentStamp = stampHolder[0]
                    val newNode = Node(value, currentTop)
                    
                    if (top.compareAndSet(currentTop, newNode, currentStamp, currentStamp + 1)) {
                        return
                    }
                }
            }
            
            fun pop(): T? {
                val stampHolder = IntArray(1)
                while (true) {
                    val currentTop = top.get(stampHolder) ?: return null
                    val currentStamp = stampHolder[0]
                    val newTop = currentTop.next
                    
                    if (top.compareAndSet(currentTop, newTop, currentStamp, currentStamp + 1)) {
                        return currentTop.value
                    }
                }
            }
        }
        ```
    - AtomicMarkableReference\<T>
        - ссылка + boolean-версия
        - можно помечать как "удаленный", например

    - когда применять
        - lock-free структуры данных с узлами 
        - object pool
        - версионирование состояния
    - когда нет
        - когда ABA проблема не проблема
        - счетчики и флаги
    - проблемы
        - штамп внутри -- int. 32 бита. 2 млрд. может заполниться
        - при каждом обновлении пересоздается внутреняя пара. этонагрузка на GC
        - не путать stamp и timestamp -- два потока могут одновременно одно и тоже время поставить
    - под капотом это просто AtomicReference\<Pair\<T, Int>>

- атомарные массивы
    - тоже самое, что и Array\<AtomicSmht> -- просто компактнее
    - методы почти такие же. просто индекс принимают в начале
    - когда 
        - массив счетчиков (наприме -- статистика HTTP ответов) 
        - гистограмма / распределение
        - кольцевой буфер
    - когде не
        - размер динамический
        - когда индекс не натуральное число, а ключ
        - тут на помощь лучше ConcurrentHashMap
        - когда счетчиков всего 10, то особо погоды это не поменяет
    - проблемы
        - false sharing
            - int занимает 4 байта, а кэш-линия 64 байт. из-за этого, при обработке в массиве, не 1 int обрабатывается(инвалидируется), а 64 / 4 = 16
            - решение либо @sun.misc.Contended
            - либо делать свой padding-класс, где просто есть лишнее поля. но JIT может сломать это
            - LongAdder
        - атомарность только на уровне элемента
            - если нужна атомарность на нескольких -- AtomicReference\<IntArray>

- Field-Updater
    - AtomicIntegerFieldUpdater\<T>
    - атомарно работать с volatile полем в существуюшем классе, без оберток AtomicInteger
    - о проблеме
        - предположиим у меня Очень много инстансов
        - это получается в каждом хранится AtomicInteger поле
        - это много весит
        - и можно сделать так -- сделать поле Volatile, а общую логику UPDATER вынести как Статический метод -- то етсь общий
    - пример
    ```kotlin
    import java.util.concurrent.atomic.AtomicIntegerFieldUpdater

    class Counter {
        @Volatile
        var value: Int = 0 
        
        companion object {
            val UPDATER: AtomicIntegerFieldUpdater<Counter> =
                AtomicIntegerFieldUpdater.newUpdater(Counter::class.java, "value")
        }
    }
    ```
    - важно
        - поле должно быть volatile
        - должно быть val
        - тип int/long/ссылка
        - не pravate
        - не final
        - не работает с приватными полями из других классов
    - использование как обычно
    - в чем прикол
        - хранит ссылку на класс и смещение поля в памяти
        - UPDATER.incrementAndGet(c)
        - берется с + смещение -- получаем адрес поля в памяти
        - потом делается CAS черещ этот адрес 
    - когда норм
        - много инстансов класса с атомарными состояниями
            - на каждое поле свой updater
        - AtomicReference создает overhead по GC
    - когде плохо
        - усложнение кода в обычном беке
        - когда нужно передавать атомарные значения как параметрё
        - использоваь kotlinx.atomicfu вообще стоит
    - проблема
        - требует указывать тип поля
        - из-за чего если тип Generic, то -- типы стираются
    - в Kotlin
        - есть kotlinx.atomicfu
        - это выглядит как обертка
        - это просто очередная bytecode компиляция
        ```kotlin
        import kotlinx.atomicfu.atomic

        class Counter {
            private val value = atomic(0)
            
            fun increment() = value.incrementAndGet()
        }
        ```

- LongAdder и LongAccumulator
    - LongAdder
        - они хранят под капотом массив ячеек
        - и это хорошо тем, что -- потоки не долбятся в одну и ту же ячейку, из-за чего количество итераций CAS-цикла увеличивалась
        - то есть -- этот процесс распределялся более равномерно и тем самым потоки меньше мешали друг другу
        - важно
            - нет методов типо "увеличь и дай", ибо
            - тогда бы постоянно пришлось бы делать суммаризацию результата, а это дорого уже звучит
            - то есть, его основная цель -- накопить, а не постоянно меняться и выдавать ответ
        - так же важно
            - sum() -- вернет точную сумму того, что было вызвано До sum.
            - но то, что поменялось в период вызова sum -- не будет учитываться
        - когда да
            - счетчик метрик
            - накопление статистики
            - статистика кешировнаия (hit/miss)
        - когда нет
            - когда нужно прочитать и принять решенеи
                - либо значение от sum может устареть
                - sum долгая сама по себе
            - часто читаешь, чем пишешь
            - когда счетчик редко используется редко
    - LongAccumulator (бинарная функция, нейтральный элемент)
        - тоже самое, но с произвольной функцией
        - когда
            - накопление максимума/минимума
            - все ассоциотивные операции (побитовые, XOR)
    - DoubleAdder
        - тоже самое
        - используют Long

    - под капотом
        - есть как обычная переменная, так и массив
        - тем самым мы как бы учиываем нагрузку

- VarHandle
    - использовали Unsafe, который еще как бы не вышел полноценно
    - из-за чего было много потенциальных проблем
        - а вдруг удалят
        - небезопасен
    - короче говоря -- типобезопасный указатель на переменную, через который можно делать атомарные операции с разными уровнями memory ordering
    - пример
    ```kotlin
    class Counter {
        @Volatile
        @JvmField  // нужно для VarHandle: иначе Kotlin генерирует геттер/сеттер
        var value: Int = 0
        
        companion object {
            val VALUE_HANDLE: VarHandle = MethodHandles
                .lookup()
                .findVarHandle(Counter::class.java, "value", Int::class.java)
        }
    }

    Counter.VALUE_HANDLE.get(c)              // Plain
    Counter.VALUE_HANDLE.getVolatile(c)      // Volatile
    Counter.VALUE_HANDLE.getAcquire(c)       // Acquire
    Counter.VALUE_HANDLE.getOpaque(c)        // Opaque
    ```
    - методы такие же +-. но теперь можно управлять уровнями доступа
        - Plain -- нет гарантий видимости
        - Opaque -- атомарность для самой переменной, но нет барьеров
        - Acquire -- парые барьеры (читатель/писатель) -- release happens-before acquire
        - Volatile -- как @Volatile

## чуть глубже

есть операции на уровне процессора

который на ARM так вообще -- там нет единой такой операции, чтобы повторить такое нужно несколько операций. поэтому -- иногда CAS-loop может случайно провалиться даже если ничего не поменялось

в JDK все эти классы и методы использовали Unsafe. потом, с обновлением, перешли на VarHandle

под капотом используют weakCompareAndSet, ибо -- для x86 разницы нет, а для ARM -- есть. в рамках ARM weakCompareAndSet быстрее чем compareAndSet

так же применяется Intrinsics -- специальный Java-метод. что JIT-компилятор заменяет на ассемблерные инструкции напрямую, минуя обычную компиляцию байткода

о реалзации LongAdder
- наследник класса Striped64
- важные элементы, что использует
    - base -- просто переменная
        - ели нагрузка низкая, то все не по массиву разбрасывается, а в эту переменную
    - cells -- если нагрузка высокая
    - Contened -- паддинг добавляет, чтобы соседние ячейки не делили кэш линию
- ну и прочие детали
    - массив растет степенями двойки, чтобы можно было использовать битовую маску вместо %  
    - лимит = количество ядер CPU
    - в Thread появился метод getProbe. probe это быстрый псевдо-хеш, что нужен для наших striped-структур 

немного о False sharing
- переменные логически независимы, но в памяти они лежат рядом и мешают друг другу
- то есть
    - мы поменяли 1 байт
    - а тянем всю линию 64 байта в кэш
    - и так же потом все ядра пдолжны будут перечитать ее
- и это норм для одной переменной, но для многих в рамках одной -- нет
- решение -- аннотация @Contended, которая добавляет паддинг до 128 байт. почему 128?
    - типо -- у нас 64 байта на кэш-линию, то есть 128 это две. и как бы хотят решить проблему того, что -- некоторые процессоры еще и соседнюю кеш-линию подтягивают, из-за чего -- проблема не ушла) поэтому надо целых две покрывать

- и вообще, важно
    - стоит наверное в Kotlin просто использовать LongAdder и ConcurrentHashMap, без этой аннотации
    - ну либо альтернатива -- самому руками паддинг написать, но это можт сломаться JIT-ом

## а как вообще в Kotlin?

в декабре 2024 вышла kotlin.concurrent.atomics

она вроде как еще не полностью готова

в пакете уже есть стандартные нам атомики
- скалярные
- массивы

у них немного отличаются названия методов, идиоматично 

из прикольного -- работает на всех платформах, в которые компилится Kotlin

а все остальыне классы, которые мы описывали в Java, тут их как бы -- нет

но -- они есть в более старой либе kotlinx.atomicfu

зачем они, если есть JDK?
- нет  Field Updater без бойлерплейта
```kotlin 
import kotlinx.atomicfu.atomic

class Counter {
    private val value = atomic(0)
    
    fun increment() = value.incrementAndGet()
    fun get() = value.value 
}
```
- multiplatform
    - важно включить плагин и зависимость, иначе fallback к obstacle оберткам

когда может быть fallback к оберткам?
- не включил плагин
- переменная Не приватная и внутреняя в классе (то есть она либо публичная, либо вне класса)
- передается как аргумент метода
- конфликты имен переменных (?)

так же библиотека дает свою lock-и

но аналога LongAdder еще нет

Идиомы(?)
- AtomicReference + immutable sealed class
    - как State Machine
- для функций пистаь extension-ы
- scope-функции для атомарного обновления

Атомики и корутины
- атомики не стопят корутины
- атомики не полходят для корутин, если
    - если нужно использовать suspend функцию внутри atomic скоупа
    - если нужны реактивные подписчики на изменения. можно использовать StateFlow

Некоторые ловушки
- боксинг при использовании AtomicReference\<Int>
- property delefgation теряет атомарность
    - пример
    ```kotlin
    class Service {
        var counter: Int by AtomicIntDelegate(0)
        
        fun increment() {
            counter++  // раскрывается как setValue(getValue() + 1). не атомарно
        }
    }
    ```
- suspend функции внутри updateAndGet не скомпилируется
    - вместо этого -- Mutex.withLock()

## немного вопросов
- когда лучше использовать synchronized, Mutex?
    - критическая секция 5+ операций
    - атомарность над несколькими полями (но если что есть AtomicReference\<Immutable>)
    - есть suspend функици
    - сложная логика ветвления с блокирующими вызовами


# Синхронизаторы
## что это
это объект для координации потоков

это не совсем lock-и -- lock просто защищает критическую секцию, а синхронизаторы -- управляют ожиданием условий

## иерархия 

- паттерны
    - Producer-Consumer, Pipeline, Worker Pool, etc. 
- высокоуровнево
    - BlockingQueue, ConcurrentHashMap, ExecutorService  
- локи и синхронизаторы
    - CountDownLatch, Semaphore, CyclicBarrier 
    - ReentrantLock, ReadWriteLock, StampedLock 
- AbstractQueuedSynchronizer
- парковки и атомики
    - LockSupport.park/unpark, VarHandle, CAS 

## LockSupport.park / unpark

у него есть методы 
- park() -- паркует текущий
- parkNanos(time)
- parkUntil(time)
- unpark(thread)

что такое парковка вообще
- jvm просит ОС приостановить поток
- ОС снимает поток с CPU
- поток спит
- пока не вызовят unpark

есть еще прикол -- внутри есть счетчик permit. и может выйти так, что -- можно несколько раз unpark(A) сделать, но это работает только на 2 раза, ибо permit не копится
```kotlin
Thread B: unpark(A)        // permit=1
Thread B: unpark(A)        // permit=1 (не накапливается!)
Thread A: park()           // permit=1 → НЕ засыпает
Thread A: park()           // permit=0 → засыпает
```

прикольно что -- park() может вернуться и без unpark(), поэтому лучше использовать в цикле
```kotlin
while (notReady) {
    LockSupport.park(A)
}
```

## AbstractQueuedSynchronizer

все работает так или иначе на нем

идея 
- хранит int state -- далее каждая реализация определяет сама, за что оно отвечает (например Semaphore -- за количество разрешений)
- и CLH-queue -- очередь потоков, что ожидают разрешения
    - она lock-free
- так же другие потоки определяют
    - tryAcquire --  когда может пройти 
    - tryRelease -- когда может освободиться

важно
- не использовать блокирующие JDK методы в корутинах
- либо в withContext(Dispatchers.IO), либо старый код

## CountDownLatch

что мы хотим "пусть поток ждет, пока N других операций не завершатся"

для этого есть наш класс. он -- обратный счетчик

он
- создается с числом N
- любой поток может вызвать countDown() -- счетчик уменьшится на 1
- 1+ потоков могут вызвать await -- и они будут ждать обнуление счетчика

код
```kotlin
import java.util.concurrent.CountDownLatch

val latch = CountDownLatch(5)
latch.countDown()
latch.await() // подождаьть

// возвращает true если дождался, false если таймаут
val ok: Boolean = latch.await(10, TimeUnit.SECONDS)

// для логов и отладки, не иначе
val current: Long = latch.count
```

он 
- идет только вниз
- countDown -- атомарный
- countDown после нуля ничего не делает 
- await -- не блокирует, могут вызывать сколько угодно потоков 

Happens-Before
- все то, что поток выполнил до countDown -- happens before того, что выполнит поток после await

ловушки
- это одноразовый класс
- если забыть countDown в finally -- дедлок
- await без timeout -- бесконечный hang
- при больших N -- await может заиснуть
- InterruptedException
```kotlin 
try {
    latch.await()
} catch (e: InterruptedException) {
    Thread.currentThread().interrupt() // сохраняем флаг
    throw e // или логика
}
```

альтернатива в kotlin
```
// JDK
val latch = CountDownLatch(3)
repeat(3) {
    thread {
        try { doWork(it) } finally { latch.countDown() }
    }
}
latch.await()

// Kotlin
runBlocking {
    val jobs = List(3) { i ->
        launch { doWork(i) }
    }
    jobs.joinAll()
}
```

еще есть CompletableDeferred
- это как своего рода CountDownLatch(1)

## CyclicBarrier

цель: N потоков выполняют работу в Фазах, каждая следующая фаза не может начаться, пока все потоки не закончат текущую

- N потоков вызывают await()
- N - 1 блокируются
- N-ый вызывает await() и все разблокируются
- можно Переиспользовать для следующей фазы

код
```kotlin 
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.BrokenBarrierException

val barrier = CyclicBarrier(3)

val barrier2 = CyclicBarrier(3) {
    println("All 3 arrived, proceeding to next phase!")
}

val arrivalIndex: Int = barrier.await() // индекс прихода. с конца отсчет. можно доп логику навесить

barrier.await(5, TimeUnit.SECONDS)
barrier.reset()
val waiting: Int = barrier.numberWaiting
val parties: Int = barrier.parties
val broken: Boolean = barrier.isBroken
```

пример
```kotlin
class MatrixProcessor(private val matrix: Array<DoubleArray>, private val workers: Int) {
    private val barrier = CyclicBarrier(workers)
    
    fun smooth(iterations: Int) {
        val rowsPerWorker = matrix.size / workers
        
        (0 until workers).map { worker ->
            thread {
                val startRow = worker * rowsPerWorker
                val endRow = if (worker == workers - 1) matrix.size else startRow + rowsPerWorker
                
                repeat(iterations) {
                    smoothRows(startRow, endRow)
                    barrier.await()
                }
            }
        }.forEach { it.join() }
    }
    
    private fun smoothRows(from: Int, to: Int) { /* ... */ }
}
```

кстати еще прикольно 
- предположим мы хотим сделать нагрузочное тестирование нашего API
- то есть -- чтобы 10к человек одновременно обратились к ресурсу
- для этого можно в цикле await поставить в начале, чтобы Накопить потоки

есть еще barrier actio n-- действие, что в конце фазы выполняется одним из потоков. и так же -- все потоки разблокируются только после его завершения

```kotlin
val barrier = CyclicBarrier(3) {
    println("All arrived! Aggregating results...")
    aggregateResults()
}
```

если упадет исключение тут, то барьер перейдет в состоние Сломанного, и всем остальным потокам И всем будущим, кто вызовет await, бросится BrokenBarrierException. чтобы оно больше не бросалось -- нужно сделтаь reset()

причины
- timeout одного из
- interrupt одного из
- сделали reset. текущие ждущие получат


внутри у него не AQS, а ReentrantLock
- ибо AQS оптимизирован под shared, а у нас тут на ожидание 

Ловушки
- используй try-cathc для await
- как получить deadlock -- в baririer action вызывать await
- предпочтительно использовать countDownLstch


## Semaphore
этот покемон тоже содержит счетчик. только его цель "не более N потокам"


Mutex -- это типо Semaphore(1), но -- Mutex lock и unlock одним и тем же потоком, а Semaphore -- разными

построен на AQS

```kotlin 
import java.util.concurrent.Semaphore

val sem = Semaphore(5)
val fairSem = Semaphore(5, true)    // 5, fair

sem.acquire()                        // 1 permit. если нету, то блокирует. может бросить InterruptedException
sem.acquire(3)                       // 3 permits

val gotIt: Boolean = sem.tryAcquire(5, TimeUnit.SECONDS)

// просто попытаться
val gotItNow: Boolean = sem.tryAcquire()

sem.release() // не блокирует
sem.release(3)
val available: Int = sem.availablePermits() // для отладки

sem.acquireUninterruptibly() // не бросает InterruptedException

// размер очереди ждущих
val waitingCount: Int = sem.queueLength
```

смотрите какой прикол можно сделать
```kotlin
val sem = Semaphore(0) 
sem.release()           // теперь permits = 1
sem.release()           // теперь permits = 2
```

что можно сделать 
- типичный rate limiter
- DIY conneciton pool 
- producer-consumer 
- как многоразовая альтернатива CountDownLatch
- но я думаю уже есть куча простых, рабочих реализаций и свой велосипед придумывать не обязательно

что такое fair и unfair (fair -- "честно, по правилам")
- false -- в очереди может кто-то и украсть permition, из-за чего
    - кто-то ждет долго (starvation)
    - но он производительнее
    - меньше пробуждений
    - да и большинство сценариев покрывает
- true -- строго в порядке очереди
    - если задачи разной длительности. чтобы короткие не забывались
    - если есть гарантия времени отклика
    - семафор контролирует важный ресурс
    - чем выше конкуренция, тем медленее


happens-before 
- все то, что сделано До release, происходит до того, что другой поток сделает после acquire вернулся

Ловушки
- release не в finally, из-за чего может переполниться семафор
- release без acquire. и прикол в том, что счетчик увеличивается тогда, динамически получается. и это как бы -- не всегда хорошо
- tryAcquire без проверки результата
- ну и да -- семафор как mutex
- Deadlock когда acquire(N)
- в корутинах использовать JDK семафор, который по своей природе блокирует поток

отличие JDK и Kotlin семафоров (точнее -- чего нет в Kotlin)
- всегда fair=true
- нет acquire(N), только 1
- acquireUninterruptibly. всегда могут быть отменены корутины
- release проверяет, был ли acquire, иначе нельзя

если нужно использовтаь какой либо JDK код в своем Kotlin, то 
- withContext(Dispatchers.IO) { jdkSem.acquire(); ... }

дальше можно сравнить Semaphore и 
- FixedThreadPool
- Channel
- Mutex/ ReentrantLock

но -- это сведется к тому, что -- через семафор их реализовать можно, но у них уже больше функционала и в некоторых деталях (какие конкретно действия он передает, есть ли владелец какой-то и тп)

## Phaser

что-то типо CyclicBarrier

проблемы CyclicBarrier
- фиксированное число участников
- хрупкий к ошибкам
- нельзя ждать Конкретную фазу
- масштабировать не очень

а Phaser это как бы -- решает. ну и кроме прочего
- interupt одного не убьет всех

он хранит в себе
- phase -- номер фазы
- registered parties
- unarrived -- сколько еще не пришло в текущей фазе

```kotlin
import java.util.concurrent.Phaser


val phaser = Phaser()
val phaser1 = Phaser(3)

val phaser2 = Phaser(parentPhaser) // тем самым можно добиться иерархии. чтобы не все за раз, а раздельно
val phaser3 = Phaser(parentPhaser, 3)

val newPhase: Int = phaser.register() // вернёт текущую фазу
val newPhase2: Int = phaser.bulkRegister(5)
val resultPhase: Int = phaser.arriveAndDeregister()
val nextPhase: Int = phaser.arriveAndAwaitAdvance()
val currentPhase: Int = phaser.arrive() // не жду
val nextPhase2: Int = phaser.awaitAdvance(currentPhase) // пока фаза не закончится, arrive не делаю
phaser.awaitAdvanceInterruptibly(currentPhase)
phaser.awaitAdvanceInterruptibly(currentPhase, 5, TimeUnit.SECONDS)

val phase: Int = phaser.phase
val registered: Int = phaser.registeredParties
val arrived: Int = phaser.arrivedParties
val unarrived: Int = phaser.unarrivedParties
val isTerminated: Boolean = phaser.isTerminated

phaser.forceTermination()

// свой авто-терминатор
override fun onAdvance(phase: Int, registeredParties: Int): Boolean {
    return registeredParties == 0 || phase >= 10 
}
```

подвохи
- я со временем начал привыкать, что -- мы регистрируем Текущий поток. хотя это все ранво продолжает выглядеть максимально неявно
- сложнее дебажить

когда 
- стоит
    - динамика, много фаз, иерархия для масштабирования 
- не стоит
    - если хватает и более простых классов, что мы описывали ранее
    - корутины

## Exchanger

в нем встречаются два и только два потока И обмениваются данными

свойства
- только 2
- один второму, второй первому -- симметричный обмен
- блокирующий -- кто раньше пришел, тот и ждет
- переиспользуемый

```kotlin 
import java.util.concurrent.Exchanger

val exchanger = Exchanger<String>()

val received: String = exchanger.exchange("my value")
val received2: String = exchanger.exchange("my value", 5, TimeUnit.SECONDS) // бросит TimeoutException
```

сценарии
- producer-consumer с двойным буфером
```kotlin 
class DoubleBufferProcessor {
    private val exchanger = Exchanger<MutableList<Item>>()
    
    fun startProducer() = thread {
        var buffer = mutableListOf<Item>()
        while (true) {
            repeat(100) {
                buffer.add(produceItem())
            }
            
            buffer = exchanger.exchange(buffer)
            buffer.clear()
        }
    }
    
    fun startConsumer() = thread {
        var buffer = mutableListOf<Item>()
        while (true) {
            buffer = exchanger.exchange(buffer)
            
            buffer.forEach { processItem(it) }
            buffer.clear()
        }
    }
}
```
- тестирование с двумя потоками
- обработка с обменом результатами

под капотом
- А
    - делает CAS null -> X
    - если все ок, то зн он первый. паркуется
- B
    - делает тоже самое, но Y
    - но там уже X, 
    - значит он второй
        - забирает X
        - кладет Y
        - будит A
- A 
    - просыпается и забирает

- кроме того
    - внутри у нас не одна ячейка, а массив

happens-before
- все то, что поток А сделал До exchange, случается до того, что поток Б сделает после exchange. и наоборот


плохо когда
- несколько потоков. тогда лучше использовать то, что выше 
- для сценариев, в которых подходят те, что мы описали выше, то лучше их
- корутины -> Channel


## Lock-и

помогает решить ряд задач, которых не было в synchronized

и кстати -- современные JVM и JIT ускоряют synchronized

### ReentrantLock 
дает то же самое, просто теперь можно всякие там timeout, прерывания и прочую ерись навешивать
```kotlin
import kotlin.concurrent.withLock

lock.withLock {
    // критическая секция
}
```

его главные фишки
- можно сделать tryLock и не блокировать все сразу
- можно с timeout, чтобы избежать deadlock
- lockInterruptibly(). ибо synchronized не обрабатывает сам
- можно сделать fair
- Интроспекция (можно наблюдать за собственными внутренними процессами)
- Condition 
    - это его задача сделать эффективное ожидание. так сказать -- разделили обязанности
```kotlin
val lock = ReentrantLock()
val notEmpty = lock.newCondition()


lock.withLock {
    while (someCondition) {  // всегда while, без if
        condition.await()    // ждать сигнала, lock временно отпускается
    }
    ... 
}

// другой поток
lock.withLock {
    // ... изменили state
    condition.signal()      // раздубудь одного
    // или
    condition.signalAll()   // всех
}
```

важно
- signal и await только под lock

пример 
```kotlin
fun transferMoney(from: Account, to: Account, amount: BigDecimal): Boolean {
    while (true) {
        if (from.lock.tryLock()) {
            try {
                if (to.lock.tryLock()) {
                    try {
                        from.balance -= amount
                        to.balance += amount
                        return true
                    } finally {
                        to.lock.unlock()
                    }
                }
            } finally {
                from.lock.unlock()
            }
        }
        Thread.sleep(Random.nextLong(10)) 
    }
}
```

### ReadWriteLock

Много читателей Или один писатель

```kotlin
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

val rwLock = ReentrantReadWriteLock()


rwLock.read {
    ...
}

rwLock.write {
    ...
}

val readLock = rwLock.readLock()
val writeLock = rwLock.writeLock()

readLock.lock()
try { ... } finally { readLock.unlock() }
```

прикол
- поток read может взять снова readLock.lock
- поотк write может снова взять writeLock.lock, ну и так же reaedLock.lock
- read не может взять wrire, иначе deadlock
- и fair тут важен

когда
- стоит
    - много read, сильно больше
    - долгое чтение. настолько, что накладные расходы значительны
- хуже обычного лока
    - если write больше, нет смысла 
    - можно использовать concurrent коллекцию

downgrading -- это когда мы в рамках write.lock{} решили взять и read.lock{}

updrading нельзя
- если поток держит read, потом берет write, то он ждет, пока все read не закончатся, включая себя самого

### StampedLock

типо: МНОГО читателей и Без блокировки, так еще и ОДНОВРЕМЕННО писать

принцип
- считывают версию
- читают данные
- если версия поменялась, то перечитывают по новой с нормальным read lock-ом

```kotlin
fun read(): Data {
    var stamp = lock.tryOptimisticRead()
    var data = readDataUnsafe()
    
    if (!lock.validate(stamp)) {
        stamp = lock.readLock()
        try {
            data = readDataUnsafe()
        } finally {
            lock.unlockRead(stamp)
        }
    }
    return data
}
```

фукнкионал
- как RWLock
- и к тому же есть optimistic read

сценарии
- крч -- везде, где чтения очень много 

подвохи
- не реентрантный (это свойство фрагмента кода, которое позволяет Безопасно вызывать его повторно До того, как завершился предыдущий вызов)
- ну и при оптимистичном чтении данные могут быть несогласнованы
- нужно делать локальные копии. ибо иначе может произойти reorder, может перепутаться порядок чтения с проверкой
- оптимистичное чтение не подходит для ссылок
- lock.tryOptimisticRead() == 0 -> неудача
- нет Condition


### Когда что стоит выбрать?
│
├─ простой случай, нет особых требований
│   └─→ synchronized (или Kotlin @Synchronized)
│
├─ tryLock, lockInterruptibly, Condition, fair
│   └─→ ReentrantLock
│
├─ Read >> Write (10:1+), долгие операции чтения
│   ├─ Read очень частый и быстрый, write редкий
│   │   └─→ StampedLock с optimistic read
│   └─ Обычный read-heavy
│       └─→ ReentrantReadWriteLock
│
├─ коллекция (Map, List, Queue)?
│   └─→ Concurrent коллекции
│        ConcurrentHashMap, CopyOnWriteArrayList, ...
│
└─ в корутинах?
    └─→ kotlinx.coroutines.sync.Mutex -- он кстати не реентраный и не имеет Condition

## ConcurrentHashMap

java7
- делился на 16 сегментов
- у каждого сегмента свой ReentrantLock
- много весил

Java8
- один большой массив бакетов
- блокировка на уровне бакета. по head ноде 
- чтение -- lock-free (через volatile)
- при коолизии -- красно-черное дерево
- CAS для пустых бакетов
- параллельный resize
    - да -- в этот момент помогают несколько потоков
    - и так же работают в это время с двумя таблицами одновременно

есть так же атомарные операции 
- compute*(...) {}

подвохи
- лямбда в compute* блокирует бакет
- если менять мапу в рамках этой же compute*, то это может привести в deadlock
- size приблизительный 
- итератор может не увидеть добавленные в это время элементы
- null не разрешен 


## BlockingQueue

паттерн Producer-Consumer
- put(item) -- блокирует, если полныая
- take() -- блокирует, если пустая

реализации
- ArrayBlockingQueue
    - на ограниченном массиве, кольцевая
    - один reentrantlock. из-за этого producer и consumer конкурируют
    - 2 Condition

- LinkedBlockingQueue
    - на локи putLock и takeLock. не конкурируют
    - динамический размер

- SynchronousQueue 
    - хранения нет. вместо этого каждый ждет каждого в случае если

- PriorityBlockingQueue
    - нет границ
    - put не блокирует
    - take блокирует, если пусто

- DelayQueue
    - элементы доступны после задержки

- LinkedTransferQueue
    - там когда делает transfer, что-то типо put
    - но ждет, пока кто-то сделает take

подвохи
- unbounded по умолчанию може привести к OOM
- add не защищает так же, как put
- poll без timeout
- fair дорогой
- может быть интерапт
- size тоже не доверять

## Какие еще есть?

- CopyOnWriteArrayList\<E> и подобные 
    - после кадой модификакции копируется весь массив внутренний
    - использовать когда много чтения и малый размер коллекции. к примеру -- список слушителей

- ConcurrentLinkedQueue\<E>
    - lock-free очередь, без блокировок, CAS
    - когда?
        - очень высокая нагрузка и не нужна блокировка

- ConcurrentSkipListMap\<K, V>
    - lock-free красно-черное дерево
    - но там skip list
    - range запросы
    