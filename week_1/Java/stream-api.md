# Stream API

здесь будет все об `Stream API`

начнем с разбора вопросов с [этого](https://telegra.ph/Voprosy-CORE-2-02-04) источника

## Содержание

- [Что такое Stream API? Для чего нужны стримы?](#что-такое-stream-api-для-чего-нужны-стримы)
- [Почему Stream называют ленивым?](#почему-stream-называют-ленивым)
- [Какие существуют способы создания стрима?](#какие-существуют-способы-создания-стрима)
- [Какие промежуточные методы в стримах вы знаете?](#какие-промежуточные-методы-в-стримах-вы-знаете)
- [Расскажите про метод peek() быстрый взгляд.](#расскажите-про-метод-peek-быстрый-взгляд)
- [Расскажите про метод map() маппинг из одного в другое](#расскажите-про-метод-map-маппинг-из-одного-в-другое)
- [Какие терминальные методы в стримах вы знаете?](#какие-терминальные-методы-в-стримах-вы-знаете)
- [Расскажите о параллельной обработке в Java 8.](#расскажите-о-параллельной-обработке-в-java-8)

## Вопросы

### Что такое Stream API? Для чего нужны стримы?

интерфейс `java.util.Stream` представляет собой последовательность элементов, над которыми можно производить различные операции

основная цель: упрощение работы с наборами данных (фильтрация, сортировка и тп)

```java
IntStream.of(50, 60, 70, 80, 90, 100, 110, 120).filter(x -> x < 90).map(x -> x + 10).limit(3).forEach(System.out::print);
```

как создать экземпляр `Stream`: об этом далее [ниже](#какие-существуют-способы-создания-стрима)

типы операций
- `промежуточная` (*intermediate*, *lazy*) - обрабатывает поступающие элементы и возвращает *Stream*. таких операций может быть много
- `терминальные` (*terminal*, *eager*) - обрабатывают элементы и завершают работу Stream. может быть только один

Важно:
- обработка не начнется до тех пор, пока не будет вызвана терминальная операция
- экземпляр stream-а нельзя использовать более одного раза

кроме универсальных stream-ов существуют и для примитивов *int*, *double*, *long*: `IntStream`, `DoubleStream`, `LongStream`

работают так же, как и обычные объектные, Но отличия в том:
- использую специальные лямба-выражения (типо `IntFunction` или `IntPredicate` вместо `Function`, `Predicate`)
- есть дополнительные конечные операции `sum()`, `average()`, `mapToObj()`

### Почему Stream называют ленивым?

вообще Ленивое программирование -- технология, которая позволяет вам отсрочить вычисления кода до Тех пор, пока не понадобится его результирующее значение

Блок обработки Это промежуточные оперции. А они не выполняются до тех пор, пока не вызоветься терминальная операция

### Какие существуют способы создания стрима?

- из коллекции: 
```java
Stream<String> fromCollection = Arrays.asList("x", "y", "z").stream();
```

- Из набора значений:
```java
Stream<String> fromValues = Stream.of("x", "y", "z");
```

- Из массива: 
```java
Stream<String> fromArray = Arrays.stream(new String[]{"x", "y", "z"});
```

- Из файла (каждая строка в файле будет отдельным элементом в стриме):
```java
Stream<String> fromFile = Files.lines(Paths.get("input.txt"));
```

- Из строки:
```java
IntStream fromString = "0123456789".chars();
```

- С помощью Stream.builder():
```java
Stream<String> fromBuilder = Stream.builder().add("z").add("y").add("z").build();
```

- С помощью Stream.iterate() (бесконечный): 
```java
Stream<Integer> fromIterate = Stream.iterate(1, n -> n + 1);
```

- С помощью Stream.generate() (бесконечный): 
```java
Stream<String> fromGenerate = Stream.generate(() -> "0");
```

### Какие промежуточные методы в стримах вы знаете?

- `concat(Stream<? extends T> a, Stream<? extends T> b)` -- объединить два потока

- `distinct()` -- потом с уникальными данными типа T

- `dropWhile(Predicate<? super T> predicate)` -- пропускает элементы, которые соответсвуют *predicate*, пока не попадется элемент, который не соответсвует условию. 

- `filter(Precicate<? super T> predicate)` -- фильтрация по условию
```java
Stream<String> citiesStream = Stream.of("Париж", "Лондон", "Мадрид","Берлин", "Брюссель");

citiesStream.filter(s->s.length()==6).forEach(s->System.out.println(s));
```

- `limit(long maxSize)`
```java
Stream<String> phoneStream = Stream.of(
    "iPhone 6 S", 
    "Lumia 950", 
    "Samsung Galaxy S 6", 
    "LG G 4",
    "Nexus 7"
);

phoneStream
    .skip(1)
    .limit(2)
    .forEach(s->System.out.println(s));
```

- `map(Function<? super T, ? extends R> mapper)` -- преобразовать поток из элементов типа T в R
```java
map(n -> n.toString())
```

- `flatMap(Function<? super T, ? extends Stream<? extends R>> mapper)` -- позволяет преобразовать элемент типа T в несколько элементов типа R и возвращает поток из R
```java
Stream<Phone> phoneStream = Stream.of(
    new Phone("iPhone 6 S", 54000),
    new Phone("Lumia 950", 45000),
    new Phone("Samsung Galaxy S 6", 40000)
);

phoneStream
    .flatMap(p->Stream.of(
        String.format("название: %s  цена без скидки: %d", p.getName(), p.getPrice()), 
        String.format("название: %s  цена со скидкой: %d", p.getName(), p.getPrice()-(int)(p.getPrice()*0.1)))
    )
    .forEach(s->System.out.println(s));
```

- `skip(long n)` -- первые n элементов skip

- `sorted()` -- по возрастанию. только для объектов, кто реализует интефейс `Comparable`. Но есть...

- `sorted(Comparator<? super T> comparator)` -- если у нас Не реализован этот интерфейс Или мы хотим какую-то свою логику. 
```java
// пример компаратора
class PhoneComparator implements Comparator<Phone>{
      public int compare(Phone a, Phone b){
              return a.getName().toUpperCase().compareTo(b.getName().toUpperCase());
    }
}
```

- `takeWhile(Predicate<? super T> predicate)` -- выбирает из потока элементы пока они соответсвуют условию. возвращаются в виде потока

Замечания:
- разница между `map()` и `flatMap()` -- *map* делает одного выходное значение во время преобразования, а *flatMap* создает 0+ значений для каждого входного

- по сути, все промежуточные операции возвращают Новый поток. а быть точнее...

- "возвращает новый поток" -- это в значении новый объект *Stream*, а не прям Новую коллекцию

### Расскажите про метод peek() быстрый взгляд.

`Stream\<T> peek(Consumer<? super T> action)` -- возвращает поток, дополнительно выполняя указанное действие с каждым элементом 

полезно 
```java
integerStream.peek(System.out::println)
```

### Какие терминальные методы в стримах вы знаете?

- `forEach(Consumer<? super T> action)` — применяет действие к каждому элементу
```java
Stream.of("A", "B", "C")
      .forEach(System.out::println);
```

- `forEachOrdered(Consumer<? super T> action)` — то же самое, но гарантирует порядок (важно для parallel stream)

- `toArray()` — собирает элементы стрима в массив
```java
Object[] arr = Stream.of(1, 2, 3).toArray();
```

- `reduce(...)` — сворачивает поток в одно значение. можно использовать для
    - суммы
    - произведения
    - конкатенации строк
    - любых агрегирующих операций
```java
int sum = Stream.of(1, 2, 3, 4)
                .reduce(0, (a, b) -> a + b);
```


- `collect(Collector)` — собирает элементы в коллекцию или другую структуру. какие есть в частности:
    - `Collectors.toList()` — в список
    
    - `Collectors.toSet()` — в множество
    
    - `Collectors.toCollection`
    
    - `.toConcurrentMap(), .toMap()`
    
    - `Collectors.groupingBy()` — группировка
    
    - `Collectors.joining()` — склейка строк
    
    - `Collectors.averagingInt()` — среднее значение. еще есть и для *double*, *long*
    
    - `.summingInt()` -- просто сумму возвращает. еще есть и для *double*, *long*
    
    - `Collectors.summarizingInt()` — статистика (min, max, avg, sum, count). еще есть и для *double*, *long*
    
    - `.partitioningBy(Predicate<? super T> predicate)` -- разделяет коллекцию на две части по соответствию условию и возвращает их как `Map<Boolean, List>`
    ```java
    Map<Boolean, List<Integer>> result =
    Stream.of(1, 2, 3, 4, 5)
          .collect(Collectors.partitioningBy(n -> n % 2 == 0));
    ```
    еще есть версия, которая сразу применяет коллектор. то есть, потом не нужно применять к List\<T>. `partitioningBy(Predicate<? super T> predicate, Collector<? super T, A, D> downstream)`

    - `groupingBy(Function<? super T, ? extends K> classifier)` -- разделяет коллекцию на несколько чатей и возвращает `Map<N, List<T>>`
    ```java
    Map<Integer, List<String>> result =
    Stream.of("one", "two", "three", "four")
          .collect(Collectors.groupingBy(String::length));
    ```

    - `mapping(Function<? super T, ? extends U> mapper, Collector<? super U, A, R> downstream)` -- дополнительные преобразования для сложных Collector-ов
    ```java
    Map<Integer, List<String>> result =
    Stream.of("one", "two", "three", "four")
          .collect(Collectors.groupingBy(
              String::length,
              Collectors.mapping(
                  String::toUpperCase,
                  Collectors.toList()
              )
          ));
    ```

```java
List<Integer> list = Stream.of(1, 2, 3)
                           .collect(Collectors.toList());
```
- `min(Comparator)` или `max`
```java
Optional<Integer> min = Stream.of(5, 2, 9).min(Integer::compareTo);
```

- `count()`
```java
long count = Stream.of(1, 2, 3).count();
```

- `findFirst()` — первый элемент (в порядке стрима)

- `findAny()`— любой элемент (часто используется в parallel stream)

- `anyMatch(Predicate)` — есть ли хотя бы один элемент, подходящий под условие
```java
boolean result = Stream.of(1, 2, 3)
                       .anyMatch(n -> n > 2);
```

- `allMatch(Predicate)`
- `noneMatch(Predicate)`

Замечание:
- терминальные операции не возвращают Stream
- после терминальной операции поток считается закрытым
```java
Stream<Integer> s = Stream.of(1, 2, 3);
s.count();
s.forEach(System.out::println); // IllegalStateException
```

### Расскажите о параллельной обработке в Java 8.

кроме последовательной, есть и параллельная обработка

параллельные стримы используют общий `ForkJoinPool.commonPool()`

при чем -- если окружение не многоядерное, то поток будет выполняться как последовательный

общий алгоритм такой: данные в стримах деляться на части, каждая часть обрабатывается в отдельном потоке, а в конце соединяются

для создания можно использовать метод из интерфейса `Collection` : `parallelStream()`

чтобы из последовательного сделать параллельный -- нужно у `Stream` вызвать `.parallel()`

`.isParallel()` -- понятно для чего метод

так же есть метод `sequential()` -- операция последовательная
```java
collection
    .stream()
    .peek(...) // операция последовательна
    .parallel()
    .map(...) // операция может выполняться параллельно,
    .sequential()
    .reduce(...) // операция снова последовательна
```

важно: порядок изначально как в источнике данных. при параллельных стримах система сохраняет порядок следования элементов

но исключением является `forEach(...)`, который может выводить элементы в произвольном порядке. чтобы сохранить порядок есть метод `forEachOrdered(...)`

что может повлиять на производительность параллельных стримов:
- размер данных (сложнее разделить и объединить)

- количество ядер процессора

- чем проще структура данных, тем быстрее происходят операции (пример: *ArrayList* и *LinkedList*. у первого последовательные и несвязанные данные, а второго последовательные, но связанные данные. поэтому второй труднее распределить)

- над примитивами операции быстрее, чем над объектами

- не рекомендуют использовать параллельные стримы для скольких-нибудь долгих операций (сетевое соединение, например). Ибо параллельные стримы работают с одним `ForkJoinPool`, из-за чего долгие операции могут остановить работу всех параллельных стримов из-за отсутствия свободных потоков в пуле

- сохранение порядка следования увеличивает издержки при выполнении. если порядок не важен, то можно его отключить
```java
collection.parallelStream()
    .sorted()
    .unordered()
    .collect(Collectors.toList());
```