# SQL

## Введение

Здесь будет собрано вся информация о языке SQL

из полезных источников -- [ответы на экзамен по СУБД](https://docs.google.com/document/d/1i617jJxmVs5W80ziFrbM2sB-52tlFR6TSHptC2pUZSg/edit?usp=sharing), думаю не будут лишними

решать задачи по SQL можно на том же LeetCode

Также -- многое сгенерированно ИИ

## Содержание

- [Вот это я понимаю -- примеры](#вот-это-я-понимаю----примеры)
    - [DDL](#ddl)
    - [DML](#dml)
    - [DQL](#dql)
- [Какие объекты можно создавать с помощью CREATE](#какие-объекты-можно-создавать-с-помощью-create)
- [JOIN. Виды](#join-виды)
- [В чем отличие процедур от функций](#в-чем-отличие-процедур-от-функций)
- [SELECT FOR UPDATE](#select-for-update)
- [Использованые мной операторы, без описания Зачем они](#использованые-мной-операторы-без-описания-зачем-они)

## Ответы

### Вот это я понимаю -- примеры

#### DDL

```sql
/* ============================================================================
   БЛОК 1: ФИЗИЧЕСКАЯ СТРУКТУРА И СОЗДАНИЕ БАЗЫ ДАННЫХ
============================================================================ */

-- 1. В Postgres нет "Файловых групп". Есть Табличные пространства (Tablespaces).
-- Они указывают физический путь на сервере, где будут лежать данные.
-- (Папки должны быть заранее созданы в ОС и принадлежать пользователю postgres)
CREATE TABLESPACE fast_ssd_space LOCATION '/var/lib/postgresql/data/fast_disk';
CREATE TABLESPACE archive_hdd_space LOCATION '/mnt/archive_disk';

-- 2. Создание базы данных с привязкой к табличному пространству
CREATE DATABASE cinemadb
    WITH 
    OWNER = postgres
    ENCODING = 'UTF8'
    TABLESPACE = fast_ssd_space;

-- Подключаемся к созданной БД
-- \c cinemadb

/* ============================================================================
   БЛОК 2: БЕЗОПАСНОСТЬ (РОЛИ, ПОЛЬЗОВАТЕЛИ, ПРАВА) - DCL
============================================================================ */

-- В Postgres нет жесткого разделения на Login и User. Есть единое понятие ROLE.
-- Роль с правом LOGIN — это пользователь. Роль без LOGIN — это группа.

-- 1. Создание пользователей (Ролей с правом входа)
CREATE ROLE cinema_admin WITH LOGIN PASSWORD 'StrongPassword123!' CREATEDB;
CREATE ROLE data_analyst WITH LOGIN PASSWORD 'ReadPassword123!';

-- 2. Создание групповой роли (без пароля и логина)
CREATE ROLE read_only_group;

-- 3. Добавление пользователя в группу
GRANT read_only_group TO data_analyst;

-- 4. Выдача прав на схему (в Postgres по умолчанию всё лежит в схеме public)
GRANT USAGE ON SCHEMA public TO read_only_group;

-- 5. Выдача прав на ВСЕ ТЕКУЩИЕ таблицы в схеме
GRANT SELECT ON ALL TABLES IN SCHEMA public TO read_only_group;

-- 6. Выдача прав на БУДУЩИЕ таблицы
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO read_only_group;

-- 7. Точечная выдача прав на столбцы
GRANT UPDATE (rating, number_of_views) ON TABLE movie TO data_analyst;

-- 8. Отзыв прав (В Postgres НЕТ команды DENY, есть только REVOKE)
REVOKE DELETE ON ALL TABLES IN SCHEMA public FROM data_analyst;


/* ============================================================================
   БЛОК 3: СОЗДАНИЕ ТАБЛИЦ (DDL) И ОГРАНИЧЕНИЯ (CONSTRAINTS)
============================================================================ */

-- 1. Простая таблица-справочник
CREATE TABLE country (
    -- GENERATED ALWAYS AS IDENTITY - современный (вместо старого SERIAL)
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, 
    name VARCHAR(100) NOT NULL UNIQUE
);

-- 2. Таблица с CHECK ограничениями и DEFAULT значениями
CREATE TABLE director (
    id INT GENERATED ALWAYS AS IDENTITY,
    country_id INT,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    birth_year INT CHECK (birth_year > 1800 AND birth_year <= EXTRACT(YEAR FROM CURRENT_DATE)),
    
    -- Именование Primary Key
    CONSTRAINT pk_director PRIMARY KEY (id),
    
    -- Внешний ключ (Foreign Key)
    CONSTRAINT fk_director_country FOREIGN KEY (country_id) 
        REFERENCES country (id) ON DELETE SET NULL
);

-- 3. Максимально сложная таблица
CREATE TABLE movie (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    director_id INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    
    -- DEFAULT значения
    rating NUMERIC(3, 2) CONSTRAINT df_movie_rating DEFAULT 0.00,
    number_of_views INT CONSTRAINT df_movie_views DEFAULT 0,
    
    -- Именованный CHECK constraint
    CONSTRAINT chk_movie_rating CHECK (rating BETWEEN 0.00 AND 10.00),
    
    -- Вычисляемый столбец (STORED означает, что он физически сохраняется на диск)
    popularity_score NUMERIC GENERATED ALWAYS AS (rating * number_of_views / 100) STORED, 
    
    -- Внешний ключ с каскадным удалением
    CONSTRAINT fk_movie_director FOREIGN KEY (director_id) 
        REFERENCES director (id) ON DELETE CASCADE
) TABLESPACE fast_ssd_space; -- Можно положить конкретную таблицу на другой диск


/* ============================================================================
   БЛОК 4: ИЗМЕНЕНИЕ СТРУКТУРЫ (ALTER TABLE), ИНДЕКСЫ И КОММЕНТАРИИ
============================================================================ */

-- 1. Добавление нового столбца
ALTER TABLE movie ADD COLUMN release_year INT NULL;

-- 2. Изменение типа данных столбца (с автоматическим кастом старых данных)
ALTER TABLE movie ALTER COLUMN title TYPE TEXT USING title::TEXT;

-- 3. Добавление ограничения (Constraint)
ALTER TABLE movie ADD CONSTRAINT chk_release_year CHECK (release_year >= 1888);

-- 4. Создание обычного индекса (B-Tree по умолчанию)
CREATE INDEX idx_movie_title ON movie(title);

-- 5. Создание Частичного индекса (Partial Index - индексируем только популярные)
CREATE INDEX idx_movie_popular ON movie(rating) WHERE rating >= 8.0;

-- 6. Добавление комментариев (Аналог Extended Properties в MS SQL)
COMMENT ON TABLE movie IS 'Таблица для хранения информации о фильмах';
COMMENT ON COLUMN movie.rating IS 'Рейтинг от 0.00 до 10.00';


/* ============================================================================
   БЛОК 5: АДМИНИСТРИРОВАНИЕ И РАЗМЕРЫ (ВЫПОЛНЯЕТСЯ SQL-ЗАПРОСАМИ)
============================================================================ */

-- 1. Узнать размер базы данных (в человекочитаемом виде - МБ, ГБ)
SELECT pg_size_pretty(pg_database_size('cinemadb'));

-- 2. Узнать размер конкретной таблицы (вместе с индексами)
SELECT pg_size_pretty(pg_total_relation_size('movie'));

-- 3. Очистка от "мертвых" строк (Специфика MVCC Postgres)
-- Обычный VACUUM (не блокирует таблицу, помечает место как свободное)
VACUUM ANALYZE movie;

-- VACUUM FULL (Аналог SHRINK в MS SQL. Полностью перестраивает таблицу, БЛОКИРУЕТ её!)
VACUUM FULL movie;


/* ============================================================================
   БЛОК 6: ОПАСНЫЕ ОПЕРАЦИИ (УДАЛЕНИЕ И ПЕРЕИМЕНОВАНИЕ)
============================================================================ */

-- 1. Принудительное отключение всех пользователей от БД (Аналог SINGLE_USER)
-- Убиваем все процессы, подключенные к cinemadb, кроме нашего собственного
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = 'cinemadb' AND pid <> pg_backend_pid();

-- 2. Переименование базы данных
ALTER DATABASE cinemadb RENAME TO cinemadb_legacy;

-- 3. Удаление базы данных
-- DROP DATABASE cinemadb_legacy;

-- 4. Удаление роли (Сначала нужно передать её объекты другому юзеру или удалить их)
-- REASSIGN OWNED BY data_analyst TO postgres;
-- DROP OWNED BY data_analyst;
-- DROP ROLE data_analyst;
```

#### DML

Data Manipulation Language -— `INSERT`, `UPDATE`, `DELETE`

```sql
/* ============================================================================
    INSERT
============================================================================ */

-- 1. Базовая вставка одной строки
INSERT INTO country (name) 
VALUES ('США');

-- 2. Множественная вставка -- быстрее, чем много одиночных
INSERT INTO director (country_id, first_name, last_name, birth_year) 
VALUES 
    (1, 'Кристофер', 'Нолан', 1970),
    (1, 'Стивен', 'Спилберг', 1946),
    (1, 'Квентин', 'Тарантино', 1963);

-- 3. Вставка данных из другой таблицы (INSERT INTO ... SELECT)
-- Например, копируем успешные фильмы в таблицу архива
INSERT INTO movie_archive (original_id, title, rating)
SELECT id, title, rating 
FROM movie 
WHERE release_year < 2000;

-- 4. UPSERT (INSERT ON CONFLICT)
-- Если фильм с таким названием уже есть, обновляем его рейтинг. Если нет — вставляем.
-- Важно: для работы ON CONFLICT на столбце (title) должен быть UNIQUE индекс!
INSERT INTO movie (director_id, title, rating)
VALUES (1, 'Начало', 8.8)
ON CONFLICT (title) 
DO UPDATE SET 
    rating = EXCLUDED.rating, -- EXCLUDED содержит значения, которые мы пытались вставить
    number_of_views = movie.number_of_views + 1;

-- 5. Игнорирование дубликатов (DO NOTHING)
INSERT INTO country (name) 
VALUES ('США')
ON CONFLICT (name) DO NOTHING;


/* ============================================================================
    UPDATE
============================================================================ */

-- 1. Базовое обновление
UPDATE movie 
SET rating = 9.0, 
    number_of_views = number_of_views + 1000
WHERE title = 'Начало';

-- 2. Обновление с использованием JOIN (В Postgres используется синтаксис FROM)
-- Задача: Увеличить рейтинг на 0.5 всем фильмам режиссеров из США
UPDATE movie m
SET rating = m.rating + 0.5
FROM director d
JOIN country c ON d.country_id = c.id
WHERE m.director_id = d.id 
  AND c.name = 'США';

-- 3. Обновление на основе подзапроса
-- Задача: Присвоить фильму ID режиссера по его имени
UPDATE movie
SET director_id = (SELECT id FROM director WHERE last_name = 'Нолан' LIMIT 1)
WHERE title = 'Интерстеллар';


/* ============================================================================
    DELETE 
============================================================================ */

-- 1. Базовое удаление
DELETE FROM movie 
WHERE rating < 2.0;

-- 2. Удаление с использованием JOIN (В Postgres используется синтаксис USING)
-- Задача: Удалить все фильмы режиссеров, родившихся до 1900 года
DELETE FROM movie m
USING director d
WHERE m.director_id = d.id 
  AND d.birth_year < 1900;

-- 3. TRUNCATE vs DELETE
-- TRUNCATE работает мгновенно, так как не пишет удаление каждой строки в лог (WAL).
-- RESTART IDENTITY сбрасывает счетчики автоинкремента (ID снова начнется с 1).
-- CASCADE удалит данные и из зависимых таблиц.
TRUNCATE TABLE movie RESTART IDENTITY CASCADE;


/* ============================================================================
    RETURNING
============================================================================ */
-- В Postgres не нужно делать SELECT после INSERT/UPDATE, чтобы узнать сгенерированный ID.

-- 1. Получить ID только что вставленной строки
INSERT INTO director (first_name, last_name, birth_year)
VALUES ('Джеймс', 'Кэмерон', 1954)
RETURNING id;

-- 2. Получить все измененные данные после UPDATE
UPDATE movie 
SET rating = rating + 0.1 
WHERE release_year = 2023
RETURNING id, title, rating AS new_rating;

-- 3. Получить данные удаленных строк (например, для логирования в приложении)
DELETE FROM movie 
WHERE number_of_views = 0
RETURNING *;


/* ============================================================================

============================================================================ */
-- Задача: Переместить старые фильмы из таблицы movie в таблицу movie_archive 
-- ОДНИМ запросом (без риска потерять данные между DELETE и INSERT).

WITH deleted_movies AS (
    -- Шаг 1: Удаляем старые фильмы и возвращаем их данные
    DELETE FROM movie 
    WHERE release_year < 1980
    RETURNING id, title, release_year
)
-- Шаг 2: Вставляем возвращенные данные в архив
INSERT INTO movie_archive (original_id, title, release_year)
SELECT id, title, release_year 
FROM deleted_movies;


/* ============================================================================
    УПРАВЛЕНИЕ ТРАНЗАКЦИЯМИ
============================================================================ */

BEGIN; -- Начало транзакции (в Postgres также можно писать START TRANSACTION)

    UPDATE account SET balance = balance - 100 WHERE user_id = 1;
    
    -- Точка сохранения (можно откатиться сюда, не отменяя всю транзакцию)
    SAVEPOINT my_savepoint; 
    
    UPDATE account SET balance = balance + 100 WHERE user_id = 2;
    
    -- Ой, ошибка! Откатываемся до точки сохранения
    ROLLBACK TO SAVEPOINT my_savepoint; 
    
    -- Пробуем перевести другому пользователю
    UPDATE account SET balance = balance + 100 WHERE user_id = 3;

COMMIT; -- Фиксируем изменения (или ROLLBACK; для полной отмены)
```

#### DQL

описание:
- У нас есть маркетплейс. Нужно вывести **Топ-3 самых щедрых покупателей в каждом регионе**, которые:
    - Совершили заказы в **2023 году**.
    - Покупали товары только из категорий с **высоким рейтингом** (средний рейтинг товаров в категории > 4.0).
    - Суммарно потратили **более 5000$**.
    - В итоговом списке должны быть: имя клиента, название региона, общая сумма трат и количество купленных уникальных товаров.


запрос
```sql
WITH HighRatedCategories AS (
    -- 1. Находим категории с высоким средним рейтингом (Агрегация + HAVING)
    SELECT 
        c.id, 
        c.name as category_name
    FROM categories c
    JOIN products p ON c.id = p.category_id
    LEFT JOIN reviews r ON p.id = r.product_id
    GROUP BY c.id, c.name
    HAVING AVG(r.rating) > 4.0
),
CustomerStats AS (
    -- 2. Собираем статистику по продажам (Куча JOIN-ов + WHERE + GROUP BY)
    SELECT 
        cu.id as customer_id,
        cu.name as customer_name,
        re.name as region_name,
        SUM(oi.price * oi.quantity) as total_spent,
        COUNT(DISTINCT oi.product_id) as unique_products_count
    FROM customers cu
    INNER JOIN regions re ON cu.region_id = re.id
    INNER JOIN orders o ON cu.id = o.customer_id
    INNER JOIN order_items oi ON o.id = oi.order_id
    INNER JOIN products p ON oi.product_id = p.id
    WHERE o.order_date BETWEEN '2023-01-01' AND '2023-12-31'
      AND p.category_id IN (SELECT id FROM HighRatedCategories) -- Фильтр по CTE
    GROUP BY cu.id, cu.name, re.name
    HAVING SUM(oi.price * oi.quantity) > 5000 -- Фильтрация агрегата
),
RankedCustomers AS (
    -- 3. Ранжируем клиентов внутри каждого региона (Оконная функция)
    SELECT 
        customer_name,
        region_name,
        total_spent,
        unique_products_count,
        DENSE_RANK() OVER (
            PARTITION BY region_name 
            ORDER BY total_spent DESC
        ) as customer_rank
    FROM CustomerStats
)
-- 4. Финальный выбор Топ-3 и сортировка
SELECT 
    customer_rank,
    customer_name,
    region_name,
    total_spent,
    unique_products_count
FROM RankedCustomers
WHERE customer_rank <= 3
ORDER BY region_name ASC, total_spent DESC;
```
### Какие объекты можно создавать с помощью CREATE

* `DATABASE` — создание новой базы данных для изоляции и хранения связанных таблиц и объектов
* пример
```sql
CREATE DATABASE shop_db;
```
* `TABLE` — базовая структура для хранения данных в виде строк и столбцов
* пример
```sql
CREATE TABLE users (
    id INT PRIMARY KEY,
    name VARCHAR(50)
);
```

* `VIEW` — виртуальная таблица, представляющая собой сохраненный SQL-запрос для упрощения сложных выборок
* пример
```sql
CREATE VIEW active_users ASSELECT id, name FROM users WHERE status = 'active';
```

* `INDEX` — структура для ускорения поиска строк в таблице
* пример
```sql
CREATE INDEX idx_user_name ON users(name);
```

* `PROCEDURE` — сохраненный набор SQL-команд, который может принимать параметры и выполнять логику на сервере
* пример
```sql
CREATE PROCEDURE delete_user(IN user_id INT)BEGIN
    DELETE FROM users WHERE id = user_id;END;
```

* `FUNCTION` — объект, который принимает аргументы, выполняет вычисления и обязательно возвращает значение
* пример
```sql
CREATE FUNCTION get_discount(price INT) RETURNS INT DETERMINISTICRETURN price * 0.1;
```

* `TRIGGER` — программа, которая автоматически запускается при наступлении событий INSERT, UPDATE или DELETE
* пример
```sql
CREATE TRIGGER after_user_signup
AFTER INSERT ON usersFOR EACH ROWINSERT INTO logs(action) VALUES ('New user registered');
```

* `SEQUENCE` — генератор уникальных последовательных чисел, обычно для автоинкремента первичных ключей
* пример
```sql
CREATE SEQUENCE user_id_seq START WITH 1 INCREMENT BY 1;
```

* `USER` / `ROLE` — создание учетной записи или группы прав для управления доступом к базе данных
* пример
```sql
CREATE USER 'analyst'@'localhost' IDENTIFIED BY 'secure_password';
```

* `SCHEMA` — логический контейнер внутри базы данных для группировки таблиц и разграничения прав
* пример
```sql
CREATE SCHEMA reporting;
```

### JOIN. Виды

подробнее можно глянуть [тут](https://antonz.ru/sql-join/)

я же, для краткости, просто оставлю эту картинку И для примера -- попрошу вас посмотреть из примеров выше

![alt text](https://drupalbook.org/sites/default/files/inline-images/visual_sql_joins_orig_kopiya.jpg)

### В чем отличие процедур от функций

| Характеристика | Функция (Function) | Процедура (Procedure) |
| :--- | :--- | :--- |
| **Возвращаемое значение** | **Обязательно** возвращает одно значение (скаляр или таблицу). | Не обязана ничего возвращать (или возвращает набор значений через `OUT`-параметры). |
| **Использование в SQL** | Можно вызывать внутри `SELECT`, `WHERE`, `HAVING`. | Нельзя использовать в `SELECT`. Вызывается отдельно через `CALL` или `EXEC`. |
| **Изменение данных (DML)** | Обычно используется только для чтения и расчетов. Изменять данные (INSERT/UPDATE) внутри часто запрещено. | Предназначена для изменения данных и выполнения сложной бизнес-логики. |
| **Транзакции** | **Нельзя** управлять транзакциями (делать `COMMIT` или `ROLLBACK`) внутри. | **Можно** фиксировать или откатывать транзакции внутри кода. |
| **Параметры** | Только входные параметры (`IN`). | Входные (`IN`), выходные (`OUT`) и смешанные (`INOUT`). |
| **Вызов других сущностей** | Может вызывать только другие функции. | Может вызывать и функции, и другие хранимые процедуры. |
| **Основная цель** | Вычислить значение на основе входных данных. | Выполнить последовательность действий над базой данных. |
| **Говоря проще** | реально как функция -- дал X, получил Y. данные вокруг не портит | это как скрипт, который может и в БД что-то поменять, и значение вернуть |

### SELECT FOR UPDATE

нужен для **пессимистической блокировки** на уровне строк. сообщает БД, что «сейчас читает эти строки, но скоро он их изменит, поэтому заблокируйте их для других, пока я не закончу транзакцию».

для предотвращения **Race Condition**

- Зачем? пример Lost Update
    - Транзакция А читает баланс: `100$`.
    - Транзакция Б читает баланс: `100$`.
    - Транзакция А прибавляет `50$` и пишет: `UPDATE balance = 150`.
    - Транзакция Б прибавляет `10$` и пишет: `UPDATE balance = 110`.
    - Если бы мы использовали `FOR UPDATE`, транзакция Б ждала бы завершения первой.

- Как это работает
    - Когда выполняете `SELECT ... FOR UPDATE`:
        - База находит нужные строки.
        - Ставит на них **эксклюзивную блокировку** (Exclusive Lock).
        - Другие транзакции, которые попытаются сделать `UPDATE`, `DELETE` или такой же `SELECT FOR UPDATE` с этими строками, будут ждать
    - Обычные `SELECT` (без блокировок) в Postgres продолжит работать и читать старую версию (благодаря MVCC).
    - Блокировка снимается только тогда, когда вы делаете `COMMIT` или `ROLLBACK`.

- модификаторы из Postgres
    - `NOWAIT` -- Если строка уже заблокирована кем-то другим, запрос не будет ждать, а сразу выбросит ошибку.
        - `SELECT ... FOR UPDATE NOWAIT;`
    - `SKIP LOCKED` -- Если строки заблокированы, запрос просто пропустит их и выдаст только свободные.
        - круто для реализации **очередей задач** (чтобы два воркера не схватили одно и то же задание).
        - `SELECT ... FOR UPDATE SKIP LOCKED LIMIT 1;`
    - `FOR UPDATE OF table1` -- если вы хотите во время JOIN запроса заблокировать только одну строку

- также
    - работает только внутри транзакции. иначе снимется мгновенно
    - есть возможность deadlock

```sql
BEGIN;

-- Блокируем конкретную строку
SELECT * FROM accounts WHERE id = 1 FOR UPDATE;

-- Тут какая-то логика приложения (расчеты)

UPDATE accounts SET balance = balance + 50 WHERE id = 1;

COMMIT; -- Только сейчас блокировка снимется
```

### Использованые мной операторы, без описания Зачем они
- DENSE_RANK()