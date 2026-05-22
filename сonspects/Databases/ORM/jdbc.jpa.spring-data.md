# Введение 

Зачем нам вообще все это?

## Немного истории
JDBC
- Java появилась в 1995
- в 1997 вышел JDBC 1.0
- но это куча SQL, куча boilerplate кода для "соединть, рассоединить, подсоединить..."

Начались попытки
- и в 2001 появился Hibernate
- MyBatis -- SQL ты, маплю я
- ну и прочие
- из минусов -- у каждого своя ORM, поэтому -- завязываешься на конкретной ORM

JPA
- решили вместе с Hibernate, в 2006, определить стандарт
- но это все равно было много кода

Spring Data JPA
- 2011
- "опиши интерфейс,  далее я сам!
- это не замена -- это слой сверху

## Важно
итого у нас вызовы такие
Spring Boot -> Spring Data Jpa -> Jpa -> Hibernate -> Jdbc -> PostgreSQL

Важно понимать, что -- это все наслойки. это не что-то новое, это просто одно поверх другого

и тут мы ищем баланс между Меньше кода И Большей производительностью

## Некоторые понятия и тезисы

Соедение открыть -- это дорого (handshake, аутентификация, инициализация сессии). поэтому используют заранее открытые соединения, которые переиспользуются -- пулы соединений 

Persistence Context -- пространство, где живут объкты, за которыми следит Hibernate

JPA -- спецификация, Hibernate -- реалиазация 

Spring Data JPA -- не замена Hibernate

Чем больше магии, тем менее предсказуема система И тем больше расходы 


# JDBC

## что это

Java Database Connectivity -- спецификация, стандарт для работы с реляциоанными БД. в пакетах у нас лежат интерфейсы

а для того, чтобы говорить с конкретной бд, нужен драйвер 

то есть 

код
   ↓ использует интерфейсы
java.sql.Connection, java.sql.PreparedStatement
   ↓ драйвер (там просто в нем реализация)
org.postgresql.jdbc.PgConnection, PgPreparedStatement (PostgreSQL JDBC Driver)
   ↓ по сетевому протоколу
PostgreSQL сервер

есть разные типы драйверов (4). но в основном сейчас тип 4 -- драйвер на чистой Java. не нативный, не полунативный. чисто Java

## сущности 

- DriverManager (старый) и DataSource (новый) -- чтобы получить соеденение в БД
- Connection -- соединение с БД. умеет
    - создавать Statement, PreparedStatement, CallableStatement
    - упрвлять транзакциями: setAutoCommit, commit, rollback
    - управоять уровнями изоляции 
    - возвращать метаданные о БД
    - Важно
        - для всего этого есть методы. я думаю если надо их можно найти. не надо мозг себе ломать
        - соединение нужно закрывать
- о Statement-ах
    - Statement
        - просто запрос без параметров
        - уязвим к SQL инъекциям
        - нет кэша плана выполнения
        - можно использовтаь только когда SQL формируется полностью статически и без пользовательских данных (Create table)
    - PreparedStatement
        - параметризованный
        - компилируется и переиспользуется БД
        - защита от SQL инъекций
    - CallableStatement
        - для хранимых процедур

- ResultSet -- курсор по результатут запроса. итератор
    - не загружает строки все за раз
    - а драйвер подгружает порциями
    - тоже нужно закрывать

- метаданные
    - ResultSetMetadata -- инфа о колонках результата
    - DatabaseMetaData -- инфа о БД

- SQLException -- иерархия исключений JDBC, checked
    - SQLIntegrityConstraintViolationException, SQLTransientException, SQLTimeoutException, SQLNonTransientException
    - есть getErrorCode от БД


пример. просто чтобы иметь представление, Что это
```java
public Optional<User> findById(Long id) {
    String sql = "SELECT id, name, email, created_at FROM users WHERE id = ?";
    
    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
        
        ps.setLong(1, id);
        
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                User user = new User();
                user.setId(rs.getLong("id"));
                user.setName(rs.getString("name"));
                user.setEmail(rs.getString("email"));
                user.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                return Optional.of(user);
            }
            return Optional.empty();
        }
    } catch (SQLException e) {
        throw new RuntimeException("Failed to find user", e);
    }
}

public Long insert(User user) {
    String sql = "INSERT INTO users (name, email) VALUES (?, ?)";
    
    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
        
        ps.setString(1, user.getName());
        ps.setString(2, user.getEmail());
        ps.executeUpdate();
        
        try (ResultSet keys = ps.getGeneratedKeys()) {
            if (keys.next()) {
                return keys.getLong(1);
            }
            throw new RuntimeException("No ID returned");
        }
    } catch (SQLException e) {
        throw new RuntimeException(e);
    }
}


```

## под капотом

### драйверы

до JDBC 4.0 драйверы нужно было подключать самому, но с появлением этой версии этот процесс автоматизировался

во всем виноват механиз Service Provider Interface (SPI) -- механизм динамического подключения реализаций Java
- в jar-нике PostgreSQL есть файл META-INF/services/java.sql.Driver
- там строчка org.postgresql.Driver
- при старте JVM и инициализации DriverManager, используется ServiceLoader -- стандартный Java API, что сканирует classpath
- находит, читает имя класса и загружает его через Class.forName
- тем временем в org.postgresql.Driver
```java
static {
    try {
        DriverManager.registerDriver(new Driver());
    } catch (SQLException e) { ... }
}
```
- он сам сембя регистрирует

вопрос -- а как DriverManager выбирает Driver?
- проходит по всем по driver.acceptsURL("jdbc:postgresql://...")
- и кто Первый, тот и победил (да, важен порядок получается)

### как работает getConnection
- DNS lookup -- IP ищем
- TCP handshake -- три пакета для TCP соединения
- TLS handshake -- если надо (если включен SSL)
- Стартовое сообщение -- драйвер посылает имя пользователя, имя бд, версию протокола
- потом Аутентификация
- Инициализируется сессия -- создается серверный процесс, выделяется память, загружаются настройки пользователя
- Все

это долго, поэтому ипользуют пулы

### PreparedStatement и Statement

Statement
- Драйвер → "SELECT * FROM users WHERE id = 5" → БД
- БД: парсит, планирует, выполняет, возвращает результат

PreparedStatement
- отправляет шаблон
    - PARSE: "SELECT * FROM users WHERE id = $1"
    - БД парсит, строит дерево разбора, запоминает под именем 
- потом параметры
    - BIND S_1, [5]
    - БД подставляет, строит план выполнения Или берет закэшированный
- EXECUTE S_1
    - БД выполняет, возвращает результат

зачем это?
- защита от SQL инъекции (параметры не интерпритируются как SQL)
- кэширование плана выполнения

Но
- есть и такие driver-ы, которые на своей стороне PreparedStatement превращают в Statement, что плохо -- мы теряем его главные преимущества 

### ResultSet

- ResultSet -- это курсор по результатам запроса
- напоминаю, что данные запрашиваются не за раз, а порциями, размер которых меняется в параметре fetch size
- и при чем -- у каждой БД, у каждого драйвера этот параметр по умолчанию разный
- у курсоров есть и типы
    - ResultSet.TYPE_FORWARD_ONLY -- только вперед
    - TYPE_SCROLL_INSENSITIVE -- туда-сюда можно. делает снапшот данных
    - TYPE_SCROLL_SENSITIVE -- туда-сюда, видит изменения БД. но это редко кем поддерживается

- Concurency
    - CONCUR_READ_ONLY
    - CONCUR_UPDATABLE

### уровни изоляции

все как обычно

### Connection Pooling

сверху мы и так много поговорили об этом. из интересного
- когда мы делаем Connection.close, то мы просто возвращаем соединение в пул, а не закрываем его
- очень крутой -- HikariCP
    - как он борется с проблемами
        - переодически проверяет соединение
        - пересоздает раз в какое-то время (30 минут) все соединения. чтобы защититься от утечек памяти
        - если кто-то взял соединение и не вернул за N секунд, то логирует stack trace

- огромное количество соединений в пуле -- не ок
    - для каждого соединения, это отдельный процесс, со своей памятью
    - и БД задохнется тем самым
    - своего рода правило: pool_size = ((cores * 2) + effective_spindle_count)

### почему DataSource
просто DriverManager делает все через статические методы

тогда как DataSource -- это интерфейс, а это можно делать разные реализации 

## Расширение и переопредиление 

и тем самым, это позволяет менять логику. например -- перехватывать соединения и логировать их 

## Проблемы

- Boilerplate
- ручной маппинг
- если есть граф объектов, то это плохо. надо самому писать SQL
- транзакции вручную 
- поменяли субд -- вероятно меняется и SQL, а значит и все -- запросы надо исправлять
- нет кэширования запросов
- надо явно делать UPDATE, нет такого что -- поменял в коде И потом оно само закинулось.
- нет состояний

# JPA

## что это?

это спецификация
- интерфейсы EntityManager, EntityManagerFactory, EntityTransaction, Query, TypedQuery и тп
- аннотации (@Entity, @Id, @OneToMany, @Table, @Column и тп)
- язык Java Persistence Query Language (JPQL)
- правила поведения

Hibernate и ей подобные -- реализация этой спецификации
- кроме этого у Hibernate есть и свой функционал, что не покрывается JPA

для управления всем этим используется persistence.xml, но в Spring Boot все в application.yaml. spring сам все генерирует

## Основные сущности и понятия

- EntityManagerFactory
    - тяжелый объект, один на БД / приложение
    - хранит
        - метаданные всех сущностей 
        - скомпиленные JPQL
        - l2-кэш
        - ссылку на DataSource
    - createEntityManager()
    
- EntityManager
    - легкий и короткоживущий 
    - представляет одну рабочую сессию в БД
    - делает
        - загружает сущности
        - сохраняет
        - удаляет
        - создает запросы
        - хранит Persistence Context
    - можно сказать, что в @Transactional что-то такое происходит
    ```java
    EntityManager em = emf.createEntityManager();
    EntityTransaction tx = em.getTransaction();
    tx.begin();
    User u = em.find(User.class, 1L);
    u.setName("New");
    tx.commit();
    em.close();
    ```

- Persistence Context
    - рабочее пространство в памяти, где hibernate отслеживает сущности
    - технически -- это Map<пара(класс сущности, ключ), сам объект>
    - это дает
        - кэш первого уровня (когда в транзакции сделал 2+ раза find,  то не будет 2+ запросов в бд)
        - гарантирует, что по Id ты получаешь один и тот же объект 
        - запоминает snapshot загруженных полей. при коммите сравнивает с текущим состоянием и если что сам генерит Update
        - не сразу идет в бд, а как бы -- копит операции и шлет их пачкой
    - немного о жизни
        - создается с EntityManager
        - закрывается с EntityManager
        - при закрытии -- все сущности становятся detached

- EntityTransaction
    - абстракция над Jdbc транзакцией
        - begin()
        - commit()
        - rollback()
    - в spring, это примерно и происходит в @Transactional
    - состояния
        - Transient
            - объект создан new, hibernate ничего не знает
        - Persistent / Managed
            - объект в Persistence Context
            - можно перейти способами 
                - em.persists
                - em.find -- сразу взять таким из БД
                - em.merge -- это можно и detached перенести
        - Detached
            - либо сессия закрылась, либо мы сами
        - Removed
            - помечен как удаленный
            - в БД не было еще delete
            - удалиться позже

## что лежит в EntityManager
```
EntityManager (= Hibernate Session)
├── Persistence Context
│   ├── entitiesByKey: Map<EntityKey, Object>
│   │     // (User, 1) -> User@a3f1
│   ├── entitySnapshots: Map<EntityKey, Object[]>
│   │     // (User, 1) -> ["Alex", "alex@mail.com"] (на момент загрузки)
│   └── entityEntries: Map<Object, EntityEntry>
│         // User@a3f1 -> { status=MANAGED, version=..., loadedState=... }
├── ActionQueue
│   ├── insertions: [User@b2e4 (новый, ждёт INSERT)]
│   ├── updates: [] (заполняется на flush после dirty check)
│   └── deletions: [User@c8d9 (помечен на DELETE)]
├── ссылка на JDBC Connection 
└── ссылка на EntityManagerFactory
```

flush()
- hibernate проходит по entitiesByKey
- для каждой сущности сравнивает Текущее состояние И Snapshot
- заполняет updates в ActionQueue
- выполняет все в указанной последовательности
- и главное -- все через PreparedStatement

## Код 
важно
- многие вещи логично что работают только если ddl-auto=create/update, а не когда есть миграция через liquibase, 
```java
import jakarta.persistence.*;

@Entity
@Table(
    name = "users",
    schema = "public",
    indexes = {
        @Index(name = "idx_email", columnList = "email"),
        @Index(name = "idx_name_age", columnList = "name, age")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_email", columnNames = "email")
    }
)
public class User {
    // @Id
    // @GeneratedValue(strategy = GenerationType.IDENTITY)
    // private Long id;
    
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_seq_gen")
    @SequenceGenerator(name = "user_seq_gen", sequenceName = "user_seq", allocationSize = 50)
    private Long id;

    @Column(
        name = "user_name",       // имя колонки
        nullable = false,          // NOT NULL
        unique = true,             // UNIQUE
        length = 100,              // VARCHAR(100), только для строк
        precision = 10, scale = 2, // для NUMERIC(10,2)
        insertable = true,         // включать в INSERT
        updatable = true,          // включать в UPDATE
        columnDefinition = "TEXT"  // ручной DDL — для случаев, когда тип нестандартный
    )
    private String name;
    

    @Transient
    private String fullName; // не будет колонкой в БД

    // Hibernate ТРЕБУЕТ public/protected конструктор без аргументов
    public User() {}
    
    // getters/setters
}
```

соглашение по именам
- userName -> user_name
- OrderItem -> order_item
- стратегии можно менять

о @GeneratedValue(strategy = GenerationType.IDENTITY)
- IDENTITY -- автоинкремент БД
    - при нем, Hibernate не может откладывать insert. ему нужно узнать id
- SEQUENCE
    - лучше, ибо можно узнать за ранее id и потом делать запросы
    - allocationSize -- сколько id берет за один заход
- TABLE
    - эмуляция SEQUENCE через отдельную таблицу
    - медленный
    - используется если нет SEQUENCE в БД
- AUTO
    - Hibernate сам выбирает стратегию на основе диалекта
- UUID
    - на стороне приложения все генерится 

## Маппинг

простые типы работают без проблем

enum-ы
- @Enumerated
    - EnumType.STRING
        - в БД будет строка
        - желательно его использовать
    - EnumType.ORDINAL
        - в БД будет число

время
- @Temporal(TemporalType.TIMESTAMP)
    - для старых java.util.Date, Calendar

большие объекты
- @Lob
```java
@Lob
private String description;  // CLOB / TEXT

@Lob
private byte[] file;         // BLOB
```

кастомный маппинг
```java
@Converter
public class YesNoConverter implements AttributeConverter<Boolean, String> {
    @Override
    public String convertToDatabaseColumn(Boolean value) {
        return Boolean.TRUE.equals(value) ? "Y" : "N";
    }
    
    @Override
    public Boolean convertToEntityAttribute(String dbData) {
        return "Y".equals(dbData);
    }
}

@Convert(converter = YesNoConverter.class)
private Boolean active;

@Converter(autoApply = true)
public class YesNoConverter ... // ко всем Boolean-полям
```

можно использовать для
- шифрования полей
- маппинга JSON
- своих типов денег, валют и прочего


@Embeddable -- встроенные объекты
- когда все в одной таблице, но логически там есть другие объекты

```java
@Embeddable
public class Address {
    private String city;
    private String street;
    private String zip;
}

@Entity
public class User {
    @Id Long id;
    
    @Embedded
    private Address address;
    
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "city", column = @Column(name = "billing_city")),
        @AttributeOverride(name = "street", column = @Column(name = "billing_street"))
    })
    private Address billingAddress;
}
```

## связи

есть которые
- owning side -- сторона, которая владеет FK и отвечает за обновление БД
- Inverse side -- обратное чтение, помечана mapperBy

Аннотации
- @ManyToOne - owning, EAGER
```java
@Entity
public class Order {
    @Id Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")  // foreign key в таблице orders
    private User user;
}
```

- @OneToMany -- inverse, LAZY 
```java
@Entity
public class User {
    @Id Long id;
    
    @OneToMany(mappedBy = "user")  // ссылается на поле в Order
    private List<Order> orders = new ArrayList<>();
}
```

- @OneToOne -- EAGER
```java
@Entity
public class User {
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private Profile profile;
}

@Entity
public class Profile {
    @OneToOne
    @JoinColumn(name = "user_id")  // FK в profiles
    private User user;
}
```

- двунаправленная связь
```java
@Entity
public class User {
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Order> orders = new ArrayList<>();
    
    public void addOrder(Order order) {
        orders.add(order); // если сделать так
        order.setUser(this); // но не сделать этого
        // то ничего не сохранится
    }
    
    public void removeOrder(Order order) {
        orders.remove(order);
        order.setUser(null);
    }
}
```
- рекомендуют использовать @ManyToOne + уникальный constraint

- @ManyToMany -- LAZY 
```java
@Entity
public class Student {
    @ManyToMany
    @JoinTable(
        name = "student_course",
        joinColumns = @JoinColumn(name = "student_id"),
        inverseJoinColumns = @JoinColumn(name = "course_id")
    )
    private Set<Course> courses = new HashSet<>();
}

@Entity
public class Course {
    @ManyToMany(mappedBy = "courses")
    private Set<Student> students = new HashSet<>();
}
```
- но зачастую делают общую таблицу и @OneToMany

## Cascade
- что делать со связанной сущностью, когда что-то случилось с родителем

пример каскада мы уже видели чуть выше в коде
```java
@OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Order> orders;
```

какие есть типы CascadeType
- PERSIST -- em.persist(user) -> persist и всех orders
- MERGE -- em.merge(user) -> merge и всех orders
- REMOVE -- em.remove(user) -> remove и всех orders
- REFRESH -- em.refresh(user) -> перечитать и всех orders
- DETACH -- em.detach(user) -> отвезать и всех orders
- ALL -- все вместе

по поводу параметра orphanRemoval
- если true, то если удалить из коллекции user.getOrders().remove(order), то удалится из БД
- а так -- не удалиться, будет просто отвязан

## Составные ключи

```java
@Embeddable
public class OrderLineId implements Serializable {
    private Long orderId;
    private Long productId;
    
    // equals/hashCode обязательно
    // ибо -- а как иначе нам сравнивть ключи?
    @Override public boolean equals(Object o) { ... }
    @Override public int hashCode() { ... }
}

@Entity
public class OrderLine {
    @EmbeddedId
    private OrderLineId id;
    
    private Integer quantity;
}
```

есть еще более старый вариант. его лучше не использовать
```java
public class OrderLineId implements Serializable {
    private Long orderId;
    private Long productId;
    // equals/hashCode
    // ...
}

@Entity
@IdClass(OrderLineId.class)
public class OrderLine {
    @Id private Long orderId;
    @Id private Long productId;
    private Integer quantity;
}
```

## Наследование
- @MappedSuperclass -- это не отдельная сущность в БД, это способ переиспользовать поля
```java
@MappedSuperclass
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;
    
    @Column(updatable = false)
    protected LocalDateTime createdAt;
    
    protected LocalDateTime updatedAt;
}

@Entity
public class User extends BaseEntity {
    private String name;
}
```

- @Inheritance
    - @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
        - пример
        ```java
        @Entity
        @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
        @DiscriminatorColumn(name = "type")
        public abstract class Payment {
            @Id Long id;
            BigDecimal amount;
        }

        @Entity
        @DiscriminatorValue("CARD")
        public class CardPayment extends Payment {
            String cardNumber;
        }

        @Entity
        @DiscriminatorValue("CASH")
        public class CashPayment extends Payment {
            String terminalId;
        }
        ```
        - payments(id, amount, type, card_number, terminal_id) -- все в одной таблице
    - @Inheritance(strategy = InheritanceType.JOINED)
        - то это три разные таблицы, через JOIN
    - TABLE_PER_CLASS
        - каждый класс -- по отдельности 
        - да -- есть дублирующие поля

    
## Version
```java
@Entity
public class Account {
    @Id Long id;
    BigDecimal balance;
    
    @Version
    private Long version;
}
```

каждый UPDATE сопровождается обновлением версии

если версия не совпала (кто-то параллельно обновил), то выпадет OptimisticLockException

## Callbacks
@PrePersist, @PostPersist, @PreUpdate, @PostUpdate, @PreRemove, @PostRemove, @PostLoad

```java
@Entity
public class User {
    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
    
    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    @PostLoad
    void postLoad() {

    }
}
```

## пример кода
```java
@Entity
@Table(name = "orders", indexes = @Index(columnList = "user_id"))
@Getter @Setter @NoArgsConstructor
public class Order extends BaseEntity {
    
    @Column(nullable = false, length = 50)
    private String number;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();
    
    @Version
    private Long version;
    
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}
```

## запросы
### JPQL

похож на SQL
```java
List<User> users = em.createQuery(
    "SELECT u FROM User u WHERE u.email = :email",
    User.class
).setParameter("email", "Email1.com")
 .getResultList();

// JOIN
List<Order> orders = em.createQuery(
    "SELECT o FROM Order o JOIN o.user u WHERE u.name = :name",
    Order.class
).setParameter("name", "Alex").getResultList();

// JOIN FETCH
List<Order> orders = em.createQuery(
    "SELECT o FROM Order o JOIN FETCH o.user WHERE o.status = :status",
    Order.class
).setParameter("status", OrderStatus.NEW).getResultList();

// Агрегация
Long count = em.createQuery(
    "SELECT COUNT(u) FROM User u WHERE u.active = true",
    Long.class
).getSingleResult();

// Update / Delete
em.createQuery("UPDATE User u SET u.active = false WHERE u.lastLogin < :date")
  .setParameter("date", LocalDateTime.now().minusYears(1))
  .executeUpdate();
```

есть так же Hibernate QL. тоже самое что и JPQL, но с доп. функциями

Native SQL -- если не хватает JPQL, но да -- завязка под конкретную БД

### Criteria API
для динамических запросов с фильтрацией
```java
CriteriaBuilder cb = em.getCriteriaBuilder();
CriteriaQuery<User> cq = cb.createQuery(User.class);
Root<User> root = cq.from(User.class);

List<Predicate> predicates = new ArrayList<>();
if (name != null) {
    predicates.add(cb.equal(root.get("name"), name));
}
if (minAge != null) {
    predicates.add(cb.greaterThanOrEqualTo(root.get("age"), minAge));
}

cq.where(predicates.toArray(new Predicate[0]));
List<User> result = em.createQuery(cq).getResultList();
```

### предкомпилируемые запросы 

```java
@Entity
@NamedQueries({
    @NamedQuery(
        name = "User.findActive",
        query = "SELECT u FROM User u WHERE u.active = true"
    )
})
public class User { ... }

em.createNamedQuery("User.findActive", User.class).getResultList();
```

### пагинация

```java
List<User> page = em.createQuery("SELECT u FROM User u ORDER BY u.id", User.class)
    .setFirstResult(20)   // OFFSET
    .setMaxResults(10)    // LIMIT
    .getResultList();
```

### DTO проекция
```java
public class UserSummary {
    private Long id;
    private String name;
    public UserSummary(Long id, String name) { this.id = id; this.name = name; }
}

List<UserSummary> result = em.createQuery(
    "SELECT new com.example.UserSummary(u.id, u.name) FROM User u",
    UserSummary.class
).getResultList();
```

## Капот

пример PesistenceContext
```java
class StatefulPersistenceContext {
    // 1. Сами сущности по ключу (класс + id)
    Map<EntityKey, Object> entitiesByKey;
    
    // 2. Метаданные о каждой сущности
    Map<Object, EntityEntry> entityEntries;
    
    // 3. Прокси для lazy-связей
    Map<EntityKey, Object> proxiesByKey;
    
    // 4. Коллекции (для @OneToMany, @ManyToMany)
    Map<CollectionKey, PersistentCollection> collectionsByKey;
}

EntityKey key = new EntityKey(1L, User.class);

class EntityEntry {
    Status status;          // MANAGED, REMOVED, DELETED, GONE
    Object[] loadedState;   // снимок полей на момент загрузки
    Object version;         // для @Version
    EntityKey entityKey;
    LockMode lockMode;
    // ...
}
```

em.find(User.class, 1L)
- EntityKey(User, 1L) в entitiesByKey
- если там есть, то возвращает сразу. в БД не нужно
- если там нет, то
    - Connection
    - готовит PreparedStatement
    - выполняет
    - получает ResultSet
    - создает через рефлексию User
    - заполняет из ResultSet
    - делает копии полей в Object[] loadedState, кладет в EntityEntry
    - кладет в entitiesByKey и в entityEntries
    - возвращает объект

em.persist(user)
- проверяет, есть ли Id
    - если Identity, то INSERT сразу кидается в БД
    - если Sequence, то INSERT откладывается
- EntityEntry с статусом MANAGED создается. loadState = текущие поля
- потом кладется в entitiesByKey
- в ActionQueue.insertions добавляется EntityInsertAction

так же важно 
- hibernate не реагирует на setter. о всех изменениях он узнает только в процессе flush, когда пройдет по всем managed-сущностям и сравнит их

## Ditry Checking

для каждой managed сущности 
- достает ее текущие значения полей
- достает loadedState из EntityEntry
- сравнивает поля
- есть отличия -- добавляет EntityUpdateAction в ActionQueue.updates

лучше
- лучше использовать immutable типы

стоимость 
- на каждый flush пройтись по всем -- дорого

можно ли отключить
- session.setDefaultReadOnly(true);
- @Transactional(readOnly = true)

## Flush
когда
- перед коммитом транзакции
- перед выполнением запроса, если может затронуть сущности 
- если явно вызвать em.flush()

em.setFlushMode(FlushModeType. ...)
- AUTO
    - default
- COMMIT
    - только перед коммитом

порядок операций
- Insetions
- Updates
- Collection removals
- Collection updates
- Collection recreations
- Deletions

такой порядок нужен, чтобы не нарушать foreign key constraints

из-за такого порядка регулярно возникают проблемы, ибо -- иногда надо иначе

и решение есть -- делать явный flush. но это будто бы дорого

## Proxy и Lazy Loading

```java
@ManyToOne(fetch = FetchType.LAZY)
private User user;
```

пишется не оригинальный объект, а прокси, который идет в БД только при первом обращении к полю. генерируется через CGLIB в runtime

что-то такое
```java
class User$HibernateProxy$xyz extends User {
    private Long id = 5L;  // только id известен
    private boolean initialized = false;
    
    @Override
    public String getName() {
        if (!initialized) {
            // ИДЁМ В БД, грузим всю сущность
            loadFromDatabase();
            initialized = true;
        }
        return super.getName();
    }
}
```

и так как -- CJLIB, то это работает только для не-final классов

так же, можно получить прокси объект без похода в бд -- em.getReference(User.class, 1L)

полезно, чтобы уменьшить количество flush
```java
@Transactional
public void linkOrder(Long orderId, Long userId) {
    User userProxy = em.getReference(User.class, userId);  // НЕТ SQL
    Order order = em.find(Order.class, orderId);            // SELECT для order
    order.setUser(userProxy);
    // на flush: UPDATE orders SET user_id = ? WHERE id = ?
    // юзера ни разу не загружали из БД!
}
```

проблемы прокси
- ломается instanceof
- equals/hashcode через сравнения полей ломаются. поэтому лучше всего использовать только id
- == при em.find и em.getReference -- дадут одну и туже ссылку

## как ResultSet в объекты превращаем

---

# немного перепрыгнул

---

## em.refresh()
что делает 
- делает SELECT для текущей сущности
- и перетерает все поля объекта на те что из БД
- то есть -- локально все изменения теряем
- Lazy сущности -- остаются Lazy
- бросит исключение, если нет в БД сущности

## Транзакции в Hibernate

когда пишу @Transactional, то
- Spring TransactionManager
    - получает EM
    - берет Hibernate Session
    - Session берет Connection из пула
    - запоминает Connection и привязвыает к транзакции
- мой код
    - em.find
    - em.persist
    - em.flush
- метод вернулся / искл
    - commit
    - rollback
- connection идет обратно в pool
- em закрывается
- persistence context уничтожается

Connection из пула берется лениво, может отдаться уже готовое соединение

уровни изоляции
- как обычно 

## Оптимистическая блокировка

проблема lost update
- оба меняли одно и тоже
- и чье-то обновление потерялось

оптимистичная блокировка
- данные не блокирую, просто проверяю что они не изменились
- например -- версией 
```java
@Version
private int version;
```
- в случае если кинет OptimisticLockException
- обрабатывается -- через try-catch
- @Lock(LockModeType.OPTIMISTIC)
    - отличается от @Version тем, что Version проверяет версию только при update
    - он там на коммите сделает еще запрос И проверит -- не изменилась ли версия
- OPTIMISTIC_FORCE_INCREMENT
    - даже если я не меняю версию, инкрементируй его версию
- когда хороша
    - конфликты редкие
    - веб приложение с формами
    - не нужно блочить БД

## Пессимистичная 

идея: при чтении я сразу блокирую строку в БД. 

LockModeType.PESSIMISTIC_* (READ, WRITE, FORCE_INCREMENT(WRITE + version))

в случае дедлока -- просто убивает одну из транзакций

чтобы задать в Spring Data -- используй @Lock

## Кэши первого и второго уровня

- L1
    - Persistent Context
    - всегда есть
    - один на EM
    - сущности хранит по (класс, id) в карте
    - при повторонном вызове find -- возвращает из карты, не SQL
    - что
        - дает 
            - гарантию идентичности в транзакции
            - меньше лишних запросов
            - основа для dirty checking
        - не дает
            - переиспользовать
            - если 1000 запросов параллельных к одному товару -- это 1000 запросов в БД
- L2
    - SessionFactory
    - между транзакциями живет
    - если сущность загружалась в БД и кэшировалась, то из этого L2 другая транзакция взять и может
    - структура 
    ```
    SessionFactory
    ├─ EntityRegion (кэш сущностей по ID)
    │   ├─ Product:1 → {id:1, name:"X", price:100, version:3}
    │   ├─ Product:2 → {...}
    │   └─ User:5 → {...}
    ├─ CollectionRegion  (тут только ID)
    │   └─ User:5.orders → [10, 11, 12]
    ├─ QueryResultsRegion
    └─ UpdateTimestampsRegion (для инвалидации)
    ```
    - это просто интерфейс, а вот реализации -- разные. при чем -- выбор конкретной будет определять Где будет лежать Кэш. тем самым -- щависит от масштаба вашего приложения
        - Ehcache
        - Caffeine
        - Hazelcast
        - Infinispan 
        - Redis
    - @Cache(usage = CacheConcurrencyStrategy.*)
        - READ_ONLY
            - для тех, что никогда не меняются
            - если обновить -- UnsupportedOperationException
        - NONSTRICT_READ_WRITE
            - которые редко меняются
            - кэш инвалидицируется -> потом БД
            - нет гарантии констистентности между транзакциями
        - READ_WRITE
            - мягкие локи в кэше
            - ставит lock на кэш -> обновляет БД -> убирает lock и инвалидирует
        - TRANSACTIONAL
            - требуется какой-то JTA
    - когда 
        - полезен
            - в основном сущности читаются
            - дорогие в построении графы объектов
            - однонодное приложение
        - вреден
            - данные часто меняются
            - несколько инстансов приложения и без распределенного кэша
            - несколько приложений пишет в одну бд. из-за этого мой кэш не узнает о чужих изменениях

то есть, как работает 
- смотрим Л1
- если нет, то Л2
- если нет, то делаем селект
- и записываем в Л1 и Л2

когда Не инвалидирует EM наш кэш
- Native запросы
- изменение другим приложением напрямую в БД
- изменения через тригеры

Query Cache -- как некоторое такое решение
- Ключ -- JPQL + параметры
- значение -- список ID найденных
- его проблема
    - инвалидируется при любом изменении затронутых таблиц
- лучше кэшировать на уровне сервиса с @Chacheable

## немного о генерации

`em.createQuery("SELECT u FROM User u WHERE u.email = :email", User.class);`
- Hibernate своим парсером строит Abstract Syntax Tree
- связывает User с таблицей, а поля с колонками
- переводит SQL для конкретной БД
- закэширует, чтобы по второму кругу не пересчитывать
- при выполнении -- PrepatedStatement создает

есть особенность в диалектах. вроде как автоматически определяется

Батчинг
```yaml
spring.jpa.properties.hibernate:
  jdbc:
    batch_size: 50
    fetch_size: 100
  order_inserts: true # группирует одинаковые INSERT-ы
  order_updates: true # группирует одинаковые UPDATE-ы
  jdbc.batch_versioned_data: true 
```

## немного об N+1

```
SELECT * FROM users;                        -- 1 запрос
SELECT * FROM orders WHERE user_id = 1;     -- запрос 1
SELECT * FROM orders WHERE user_id = 2;     -- запрос 2
SELECT * FROM orders WHERE user_id = 3;     -- запрос 3
...
SELECT * FROM orders WHERE user_id = 100;   -- запрос 100
```

решения
- JOIN FETCH
    - но -- не работает пагинация (если 1 юзер - 3 заказа, то если 10 юзеров, то 30 заказов. итого в результате нашего запроса получаем 30 строк. и вот по ним нам дали возможность пагинировать. то есть -- мы можем случайно и обрезать)
    - тогда нужно делать на два запроса
        - один на юзеров
        - потом по ним пагинировать, а потом уже дозагружать заказы
- @EntityGraph
    - Hibernate сам построит там JOIN FETCH
- @BatchSize
    - чтобы грузить коллекции пачками
    ```
    SELECT * FROM users;
    -- цикл по юзерам, доступ к orders
    SELECT * FROM orders WHERE user_id IN (1,2,3,...,20);   -- одна пачка из 20
    SELECT * FROM orders WHERE user_id IN (21,22,...,40);   -- следующая
    ```
    - запросов при это N/batchSize
- @Fetch(FetchMode.SUBSELECT)
```
@OneToMany(mappedBy = "user")
@Fetch(FetchMode.SUBSELECT)
private List<Order> orders;

SELECT * FROM orders WHERE user_id IN (
    SELECT id FROM users WHERE ...  -- тот же запрос, что был для юзеров
);
```
- DTO-проекция

## Бесконечная рекурсяи при сериализации
```java
@Entity
// вариант 3: @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
// при повтроной встрече -- просто id вставит
public class User {
    @OneToMany(mappedBy = "user")
    // вариант 2: @JsonManagedReference
    private List<Order> orders;
}

@Entity
public class Order {
    @ManyToOne
    //вариант 1: @JsonIgnore -- решение
    //вариант 2: @JsonBackReference
    private User user;
}
// вариант 4: DTO
// public record UserDto(Long id, String name, List<OrderDto> orders) {}
// public record OrderDto(Long id, BigDecimal total) {}  // без user

```

## о equals и hashCode
- при изменении объекта меняется и hashCode
- это ломает hashMap
- правила
    - не использовать сгенерированные id в hashCode
    - далее
        - либо hashCode -- константа. но тогда превратится в список или в дерево
        - либо UUID генерировать в конструкторе
        - либо использоваьт бизнес ключ
    - не испльзовать @Data / EqualsAndHashCode без полей в Lombok

# Spring Data
