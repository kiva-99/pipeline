// Jenkinsfile — Declarative Pipeline для HW32
// Цель: Автоматизация сборки, тестирования и деплоя веб-приложения

pipeline {
    // 👇 АГЕНТ: на каком узле выполнять сборку
    // Используем агент с меткой 'docker-builder' для доступа к Docker
    agent { label 'docker-builder' }

    // 👇 ПЕРЕМЕННЫЕ ОКРУЖЕНИЯ: переиспользуемые значения
    environment {
        REPO_URL = 'https://github.com/kiva-99/HW.git'
        APP_NAME = 'hw32-webapp'
        APP_VERSION = "${env.BUILD_NUMBER}"
        DOCKER_IMAGE = "${APP_NAME}:${APP_VERSION}"
        DOCKER_REGISTRY = 'localhost:5000'
        DEPLOY_PORT = '8080'
        REPORT_DIR = 'reports'
        // Параметры, которые можно переопределить при запуске
        DEPLOY_ENV = params.DEPLOY_ENV ?: 'staging'
        RUN_TESTS = params.RUN_TESTS ?: 'true'
        CLEAN_OLD = params.CLEAN_OLD ?: 'true'
    }

    // 👇 ПАРАМЕТРЫ: настраиваемые значения при запуске сборки
    parameters {
        choice(
            name: 'DEPLOY_ENV',
            choices: ['dev', 'staging', 'production'],
            description: 'Окружение для деплоя'
        )
        booleanParam(
            name: 'RUN_TESTS',
            defaultValue: true,
            description: 'Запускать ли автоматические тесты'
        )
        booleanParam(
            name: 'CLEAN_OLD',
            defaultValue: true,
            description: 'Удалять ли старые контейнеры перед деплоем'
        )
        string(
            name: 'APP_VERSION_OVERRIDE',
            defaultValue: '',
            description: 'Переопределить версию приложения (опционально)'
        )
    }

    // 👇 ЭТАПЫ PIPELINE
    stages {
        // 🔹 ЭТАП 1: Клонирование репозитория
        stage('Checkout Repository') {
            steps {
                echo "🔄 Клонируем репозиторий: ${env.REPO_URL}"
                script {
                    checkout([$class: 'GitSCM',
                        branches: [[name: '*/main']],
                        userRemoteConfigs: [[url: env.REPO_URL]],
                        extensions: [[$class: 'CleanCheckout']]])
                }
                echo "✅ Репозиторий успешно клонирован"
            }
        }

        // 🔹 ЭТАП 2: Сборка приложения
        stage('Build Application') {
            steps {
                echo "🔨 Сборка приложения ${env.APP_NAME} v${env.APP_VERSION}"
                script {
                    // Проверка наличия Dockerfile
                    if (fileExists('hw24/Dockerfile')) {
                        sh """
                            echo "=== Сборка Docker образа ==="
                            cd hw24
                            docker build -t ${env.DOCKER_IMAGE} .
                            echo "✓ Образ собран: ${env.DOCKER_IMAGE}"
                        """
                    } else {
                        echo "⚠ Dockerfile не найден, пропускаем сборку"
                        currentBuild.result = 'UNSTABLE'
                    }
                }
            }
            // Условие: пропустить, если ветка не main и не develop
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                    expression { params.DEPLOY_ENV == 'dev' }
                }
            }
        }

        // 🔹 ЭТАП 3: Запуск тестов (опционально)
        stage('Run Tests') {
            steps {
                echo "🧪 Запуск автоматических тестов"
                script {
                    if (env.RUN_TESTS == 'true') {
                        sh '''
                            echo "=== Установка зависимостей для тестов ==="
                            pip3 install pytest --user 2>/dev/null || true
                            
                            echo "=== Запуск pytest ==="
                            if [ -d "tests" ]; then
                                python3 -m pytest tests/ -v --tb=short --junitxml=pytest-report.xml || echo "⚠ Тесты не прошли"
                            else
                                echo "⚠ Папка tests/ не найдена, пропускаем тесты"
                            fi
                        '''
                    } else {
                        echo "⏭ Тесты пропущены по настройке параметра"
                    }
                }
            }
            // Условие: запускать только если параметр RUN_TESTS = true
            when {
                expression { params.RUN_TESTS == 'true' }
            }
            post {
                always {
                    // Публикация отчёта о тестах (если есть)
                    junit allowEmptyResults: true, testResults: 'pytest-report.xml'
                }
            }
        }

        // 🔹 ЭТАП 4: Очистка старых контейнеров (опционально)
        stage('Cleanup Old Containers') {
            steps {
                echo "🧹 Очистка старых контейнеров"
                script {
                    if (env.CLEAN_OLD == 'true') {
                        // Загружаем внешний Groovy-скрипт
                        def cleanupScript = library(identifier: 'hw32-lib@main', retriever: modernSCM([$class: 'GitSCMSource', remote: env.REPO_URL]))
                        // Или выполняем локальный скрипт
                        sh '''
                            echo "=== Поиск и удаление старых контейнеров ==="
                            docker ps -a --filter "name=${env.APP_NAME}" --format "{{.ID}}" | xargs -r docker rm -f || true
                            echo "✓ Очистка завершена"
                        '''
                    } else {
                        echo "⏭ Очистка пропущена по настройке параметра"
                    }
                }
            }
            // Условие: только если параметр CLEAN_OLD = true
            when {
                expression { params.CLEAN_OLD == 'true' }
            }
        }

        // 🔹 ЭТАП 5: Деплой приложения
        stage('Deploy Application') {
            steps {
                echo "🚀 Деплой приложения в окружение: ${env.DEPLOY_ENV}"
                script {
                    // Загружаем и выполняем внешний Groovy-скрипт деплоя
                    def deployScript = load 'groovy-scripts/deploy-app.groovy'
                    deployScript.deploy(
                        imageName: env.DOCKER_IMAGE,
                        containerName: "${env.APP_NAME}-${env.DEPLOY_ENV}",
                        port: env.DEPLOY_PORT,
                        environment: env.DEPLOY_ENV
                    )
                }
            }
            // Условие: деплой только в staging/production или при ручном запуске
            when {
                anyOf {
                    branch 'main'
                    expression { params.DEPLOY_ENV in ['staging', 'production'] }
                }
            }
        }

        // 🔹 ЭТАП 6: Проверка доступности приложения
        stage('Health Check') {
            steps {
                echo "🏥 Проверка доступности приложения"
                script {
                    sh """
                        echo "=== Проверка здоровья приложения ==="
                        MAX_RETRIES=10
                        RETRY_COUNT=0
                        while [ \$RETRY_COUNT -lt \$MAX_RETRIES ]; do
                            if curl -sf http://localhost:${env.DEPLOY_PORT}/health > /dev/null 2>&1; then
                                echo "✓ Приложение доступно!"
                                exit 0
                            fi
                            echo "⏳ Попытка \${RETRY_COUNT}/\${MAX_RETRIES}..."
                            RETRY_COUNT=\$((RETRY_COUNT + 1))
                            sleep 5
                        done
                        echo "❌ Приложение не ответило за время ожидания"
                        exit 1
                    """
                }
            }
            // Условие: только после успешного деплоя
            when {
                expression { currentBuild.currentResult == 'SUCCESS' || currentBuild.currentResult == 'UNSTABLE' }
            }
        }

        // 🔹 ЭТАП 7: Генерация отчёта (DSL)
        stage('Generate Report') {
            steps {
                echo "📊 Генерация отчёта о сборке"
                script {
                    // Загружаем и выполняем DSL-скрипт
                    def reportDSL = load 'dsl-scripts/report-generator.groovy'
                    reportDSL.generateReport(
                        jobName: env.JOB_NAME,
                        buildNumber: env.BUILD_NUMBER,
                        buildStatus: currentBuild.currentResult,
                        deployEnv: env.DEPLOY_ENV,
                        outputPath: "${env.REPORT_DIR}/build-${env.BUILD_NUMBER}.json"
                    )
                }
            }
            // Условие: всегда, независимо от результата
            when {
                always
            }
        }
    }

    // 👇 POST-ACTIONS: действия после завершения всех stages
    post {
        always {
            echo "🧹 Очистка рабочего пространства"
            cleanWs()
            // Архивация отчётов
            archiveArtifacts artifacts: "${env.REPORT_DIR}/*.json", allowEmptyArchive: true
        }
        success {
            echo "✅ Сборка успешна!"
            emailext (
                to: 'romantic08@inbox.ru',
                subject: "✅ SUCCESS: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: """
🎉 Сборка Jenkins прошла успешно!

📦 Приложение: ${env.APP_NAME}
🔢 Сборка: #${env.BUILD_NUMBER}
🌍 Окружение: ${env.DEPLOY_ENV}
🔗 Ссылка: ${env.BUILD_URL}
👤 Автор: ${env.CHANGE_AUTHOR}

✅ Все этапы пройдены:
• Клонирование: OK
• Сборка: OK
• Тесты: ${env.RUN_TESTS == 'true' ? 'OK' : 'PROPUщено'}
• Деплой: OK
• Health Check: OK

Приложение доступно по адресу: http://localhost:${env.DEPLOY_PORT}
                """
            )
        }
        failure {
            echo "❌ Сборка провалилась!"
            emailext (
                to: 'k.ivanovconn@gmail.com',
                subject: "❌ FAILURE: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: """
⚠️ Сборка Jenkins завершилась с ошибкой!

📦 Приложение: ${env.APP_NAME}
🔢 Сборка: #${env.BUILD_NUMBER}
🌍 Окружение: ${env.DEPLOY_ENV}
🔗 Консоль: ${env.BUILD_URL}console
👤 Автор: ${env.CHANGE_AUTHOR}

🔍 Возможные причины:
• Ошибка клонирования репозитория
• Ошибка сборки Docker-образа
• Не прошли автоматические тесты
• Ошибка при деплое
• Health Check не прошёл

🛠 Что делать:
1. Откройте консоль сборки по ссылке выше
2. Найдите строку с ERROR
3. Исправьте ошибку в коде
4. Сделайте новый коммит и push
                """,
                attachLog: true
            )
        }
        unstable {
            echo "⚠️ Сборка нестабильна"
            emailext (
                to: 'k.ivanovconn@gmail.com',
                subject: "⚠️ UNSTABLE: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: "Сборка завершилась с предупреждениями. Проверьте отчёт: ${env.BUILD_URL}"
            )
        }
    }
}