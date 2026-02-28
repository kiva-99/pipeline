# HW32 — Jenkins Pipeline, Groovy и DSL Job



## 📋 Описание
Изучение и практическое применение Jenkins Pipeline, написание скриптов на Groovy и работа с DSL для автоматизации задач.

## 🏗 Структура проекта
'''
hw32-pipeline/
├── Jenkinsfile # Основной декларативный pipeline
├── README.md # Документация
├── .gitignore # Исключаемые файлы для Git
├── groovy-scripts/
│ ├── deploy-app.groovy # Скрипт деплоя Docker-контейнера
│ └── cleanup-container.groovy # Скрипт очистки старых контейнеров
└── dsl-scripts/
└── report-generator.groovy # DSL для генерации отчётов
'''


## 🚀 Быстрый старт

### 1. Подготовка Jenkins
- Убедитесь, что Jenkins запущен и доступен: http://localhost:8080
- Установлены плагины: **Pipeline**, **Git**, **Docker**, **Email Extension**
- Настроен агент с меткой `docker-builder` (имеет доступ к Docker socket)

### 2. Создание задачи в Jenkins
1. **New Item** → введите имя `HW32-Pipeline` → выберите **Pipeline** → **OK**
2. В разделе **Pipeline** → **Definition** выберите:
   - ✅ **Pipeline script from SCM** (если Jenkinsfile в репозитории)
   - Или **Pipeline script** и вставьте содержимое `Jenkinsfile`
3. Укажите путь к скрипту: `hw32-pipeline/Jenkinsfile`
4. Нажмите **Save**

### 3. Запуск сборки
- Нажмите **Build Now** для ручного запуска
- Или дождитесь автоматического запуска (если настроен Polling SCM)

### 4. Параметры сборки
При запуске можно указать:

| Параметр | Тип | Значения по умолчанию | Описание |
|----------|-----|----------------------|----------|
| `DEPLOY_ENV` | Choice | `staging` | Окружение: `dev` / `staging` / `production` |
| `RUN_TESTS` | Boolean | `true` | Запускать ли автоматические тесты |
| `CLEAN_OLD` | Boolean | `true` | Удалять ли старые контейнеры перед деплоем |
| `APP_VERSION_OVERRIDE` | String | *(пусто)* | Переопределить версию приложения |

## 📦 Этапы Pipeline

Описание этапов:
Этап
Описание
Условие выполнения
Checkout Repository
Клонирование репозитория из Git
Всегда
Build Application
Сборка Docker-образа из hw24/Dockerfile
Ветка main/develop или DEPLOY_ENV=dev
Run Tests
Запуск pytest и публикация JUnit-отчёта
RUN_TESTS = true
Cleanup Old Containers
Удаление старых версий контейнеров
CLEAN_OLD = true
Deploy Application
Запуск нового контейнера с приложением
Ветка main или DEPLOY_ENV in [staging, production]
Health Check
Проверка доступности приложения по /health
После успешного деплоя
Generate Report
Генерация JSON-отчёта о сборке
Всегда
## 🔧 Groovy-скрипты
deploy-app.groovy
Функция для деплоя Docker-контейнера:
deploy(
    imageName: 'myapp:1.0',
    containerName: 'myapp-prod',
    port: '8080',
    environment: 'production'
)

Параметры:
imageName — имя Docker-образа
containerName — имя запускаемого контейнера
port — порт для маппинга (host:container)
environment — переменная окружения APP_ENV

cleanup-container.groovy

cleanup(
    appName: 'myapp',
    keepLast: 2,      // Сохранить 2 последние версии
    dryRun: false,    // false = реально удалить
    cleanupImages: true
)

## 📊 DSL для отчётов
Пример использования в Pipeline:
stage('Generate Report') {
    steps {
        script {
            def reportDSL = load 'dsl-scripts/report-generator.groovy'
            reportDSL.generateReport(
                jobName: env.JOB_NAME,
                buildNumber: env.BUILD_NUMBER,
                buildStatus: currentBuild.currentResult,
                deployEnv: env.DEPLOY_ENV,
                outputPath: 'reports/build-report.json'
            )
        }
    }
}

Декларативный DSL (альтернатива):

reportDSL.report(type: 'detailed', outputPath: 'reports/detailed.json') {
    type 'metrics'
    output 'reports/metrics.json'
    filter 'minDuration', 60
    includeMetrics true
    includeLogs false
}

## 📁 Формат отчёта (JSON)

{
  "report": {
    "type": "summary",
    "generatedAt": "2026-02-22 18:30:00",
    "jenkins": {
      "job": "HW32-Pipeline",
      "build": 15,
      "status": "SUCCESS",
      "url": "http://localhost:8080/job/HW32-Pipeline/15/"
    },
    "deployment": {
      "environment": "staging",
      "timestamp": 1708617000000
    },
    "metrics": {
      "durationSeconds": 120,
      "testsTotal": 10,
      "testsPassed": 10,
      "testsFailed": 0,
      "environment": "staging",
      "buildStatus": "SUCCESS"
    }
  }
}

## 🛠 Управление ошибками
Pipeline включает многоуровневую обработку ошибок:
На уровне stage: try-catch в скриптах Groovy
На уровне pipeline: блоки post { failure { ... } }
Уведомления: Email при провале сборки с прикрепленным логом
Статус сборки: UNSTABLE при предупреждениях, FAILURE при критических ошибках
## 🔐 Безопасность
SSH-ключи хранятся в Jenkins Credentials, не в коде
Пароли и токены передаются через переменные окружения
Docker-агент запущен с минимально необходимыми правами
Скрипты проверяют входные параметры перед выполнением
## 🧪 Тестирование
Локальная проверка Groovy-скриптов:
Проверка синтаксиса Groovy

groovyc groovy-scripts/*.groovy

Запуск deploy-app.groovy в режиме dry-run

groovy -DdryRun=true groovy-scripts/deploy-app.groovy

## Тестирование Pipeline:
Создайте тестовую ветку в Git
Внесите изменения в Jenkinsfile
Запустите сборку с параметром DEPLOY_ENV=dev
Проверьте логи и результаты в Jenkins UI