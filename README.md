# HW32-Pipeline — CI/CD Pipeline для автоматизации сборки и деплоя

## Описание
Декларативный Jenkins Pipeline для автоматизации сборки, тестирования и деплоя веб-приложения (hw24) с использованием Docker.

## Архитектура проекта

### Репозитории
1. **pipeline** (https://github.com/kiva-99/pipeline)
   - Jenkinsfile — основной скрипт пайплайна
   - groovy-scripts/deploy-app.groovy — скрипт деплоя Docker-контейнера
   - dsl-scripts/report-generator.groovy — генерация JSON-отчёта о сборке

2. **HW** (https://github.com/kiva-99/HW)
   - hw24/Dockerfile — Dockerfile для Nginx-приложения
   - hw24/index.html — статическая HTML-страница

### Структура пайплайна

#### Этапы (Stages):

1. **Checkout Application**
   - Клонирует репозиторий HW в папку src-app
   - Переключается на ветку main

2. **Build Application**
   - Проверяет наличие Dockerfile в hw24/
   - Собирает Docker-образ с тегом hw32-webapp:${BUILD_NUMBER}

3. **Run Tests** (опционально)
   - Устанавливает pytest
   - Запускает тесты, если папка tests/ существует
   - Публикует отчёт JUnit

4. **Cleanup Old Containers** (опционально)
   - Удаляет старые контейнеры с именем hw32-webapp

5. **Deploy Application**
   - Загружает внешний скрипт deploy-app.groovy
   - Останавливает и удаляет предыдущий контейнер
   - Запускает новый контейнер на порту 8090

6. **Health Check**
   - Проверяет, запущен ли контейнер
   - Выполняет curl внутри контейнера для проверки Nginx
   - Выводит логи при ошибке

7. **Generate Report**
   - Генерирует JSON-отчёт о сборке
   - Сохраняет в reports/build-${BUILD_NUMBER}.json

## Параметры запуска

| Параметр | Тип | Значение по умолчанию | Описание |
|----------|-----|----------------------|----------|
| DEPLOY_ENV | choice | dev | Окружение: dev/staging/production |
| RUN_TESTS | boolean | true | Запускать автоматические тесты |
| CLEAN_OLD | boolean | true | Удалять старые контейнеры |
| APP_VERSION_OVERRIDE | string | (пусто) | Переопределить версию приложения |

## Переменные окружения

- `APP_REPO_URL`: https://github.com/kiva-99/HW.git
- `APP_SRC_DIR`: src-app
- `APP_NAME`: hw32-webapp
- `DEPLOY_PORT`: 8090
- `REPORT_DIR`: reports

## Как запустить

### Через веб-интерфейс Jenkins:
1. Откройте Jenkins: http://localhost:8080
2. Найдите задачу HW32-Pipeline
3. Нажмите "Build with Parameters"
4. Выберите параметры:
   - DEPLOY_ENV: dev (или staging/production)
   - RUN_TESTS: true/false
   - CLEAN_OLD: true
5. Нажмите "Start"

### Через Git (автоматически):
Pipeline настроен на получение кода из репозитория pipeline.git.
При push изменений в ветку main Jenkins автоматически запустит сборку.

## Ожидаемые результаты

### Успешная сборка:
- ✅ Все этапы выполнены
- ✅ Docker-образ собран: hw32-webapp:${BUILD_NUMBER}
- ✅ Контейнер запущен: hw32-webapp-dev (или staging/production)
- ✅ Приложение доступно: http://localhost:8090/
- ✅ Email-уведомление отправлено на k.ivanovconn@gmail.com
- ✅ JSON-отчёт сохранён в артефактах

### Артефакты:
После успешной сборки во вкладке "Artifacts" доступен файл:
- reports/build-${BUILD_NUMBER}.json

## Обработка ошибок

### Возможные проблемы и решения:

1. **"Dockerfile не найден"**
   - Проверьте, что репозиторий HW содержит папку hw24/Dockerfile
   - Убедитесь, что ветка main существует

2. **"Port 8090 already allocated"**
   - Порт 8090 занят другим контейнером
   - Выполните: docker ps --filter "publish=8090"
   - Удалите конфликтующий контейнер: docker rm -f <container_id>

3. **"Health Check failed"**
   - Nginx внутри контейнера не запустился
   - Проверьте логи: docker logs hw32-webapp-dev
   - Убедитесь, что index.html существует в hw24/

4. **"NoSuchMethodError: deploy"**
   - Проверьте, что groovy-scripts/deploy-app.groovy существует
   - Убедитесь, что функция называется `def deploy(Map config)`

## Примечания

### Почему Docker build вместо Maven/Gradle?
Приложение hw24 является статическим веб-сайтом (HTML + Nginx), поэтому классическая сборка через Maven/Gradle не применима. Этап "Build" выполняет `docker build`, что является стандартным способом сборки контейнеризированных приложений.

### Почему Health Check через docker exec?
Проверка через `docker exec <container> curl localhost:80` надёжнее сетевой проверки, так как обходит проблемы сетевой изоляции между контейнером Jenkins-агента и развёрнутым приложением.

### Почему порт 8090?
- Порт 8080 занят Jenkins Master
- Порт 8081 занят приложением hw25-web
- Порт 8090 свободен и используется для hw32-webapp

## Структура файлов
'''
pipeline/
├── Jenkinsfile # Основной скрипт пайплайна
├── groovy-scripts/
│ └── deploy-app.groovy # Скрипт деплоя Docker
└── dsl-scripts/
└── report-generator.groovy # Генератор JSON-отчётов
'''