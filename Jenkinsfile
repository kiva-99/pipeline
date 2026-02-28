// Jenkinsfile — Declarative Pipeline для HW32
// Архитектура: Скрипт в репо 'pipeline', Приложение в репо 'HW'
// Автор: Иванов Кирилл Константинович

pipeline {
    // 👇 АГЕНТ: Используем агент с Docker
    agent { label 'docker-builder' }

    // 👇 ПАРАМЕТРЫ: Настраиваемые значения при запуске
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

    // 👇 ПЕРЕМЕННЫЕ ОКРУЖЕНИЯ
    environment {
        // URL репозитория с ПРИЛОЖЕНИЕМ (не путать с репо пайплайна)
        APP_REPO_URL = 'https://github.com/kiva-99/HW.git'
        // Папка, куда склонируем приложение
        APP_SRC_DIR = 'src-app'
        
        APP_NAME = 'hw32-webapp'
        // Версия: либо параметр, либо номер сборки
        APP_VERSION = "${params.APP_VERSION_OVERRIDE ?: env.BUILD_NUMBER}"
        
        DOCKER_IMAGE = "${APP_NAME}:${APP_VERSION}"
        DEPLOY_PORT = '8080'
        REPORT_DIR = 'reports'
        
        // Присваиваем параметры переменным окружения для удобства
        DEPLOY_ENV = "${params.DEPLOY_ENV}"
        RUN_TESTS_FLAG = "${params.RUN_TESTS}"
        CLEAN_OLD_FLAG = "${params.CLEAN_OLD}"
    }

    stages {
        // 🔹 ЭТАП 1: Подготовка и клонирование приложения
        stage('Checkout Application') {
            steps {
                echo "🔄 Клонируем репозиторий приложения: ${env.APP_REPO_URL}"
                script {
                    // Клонируем приложение в отдельную папку, чтобы не смешивать с Jenkinsfile
                    sh """
                        git clone ${env.APP_REPO_URL} ${env.APP_SRC_DIR}
                        cd ${env.APP_SRC_DIR}
                        git checkout main
                        echo "✅ Приложение клонировано в папку ${env.APP_SRC_DIR}"
                        ls -la
                    """
                }
            }
        }

        // 🔹 ЭТАП 2: Сборка приложения (Docker)
        stage('Build Application') {
            steps {
                echo "🔨 Сборка приложения ${env.APP_NAME} v${env.APP_VERSION}"
                script {
                    // Путь к Dockerfile теперь внутри склонированной папки
                    def dockerfilePath = "${env.APP_SRC_DIR}/hw24/Dockerfile"
                    
                    if (fileExists(dockerfilePath)) {
                        sh """
                            echo "=== Сборка Docker образа ==="
                            cd ${env.APP_SRC_DIR}/hw24
                            docker build -t ${env.DOCKER_IMAGE} .
                            echo "✓ Образ собран: ${env.DOCKER_IMAGE}"
                        """
                    } else {
                        echo "⚠ Dockerfile не найден по пути: ${dockerfilePath}"
                        // Не останавливаем сборку жестко, но помечаем как нестабильную
                        currentBuild.result = 'UNSTABLE'
                    }
                }
            }
            // Условие: запускаем, если это не production (для теста) или ветка main
            when {
                anyOf {
                    branch 'main'
                    expression { params.DEPLOY_ENV != 'production' }
                }
            }
        }

        // 🔹 ЭТАП 3: Запуск тестов
        stage('Run Tests') {
            steps {
                echo "🧪 Запуск автоматических тестов"
                script {
                    if (params.RUN_TESTS) {
                        sh """
                            echo "=== Установка зависимостей ==="
                            pip3 install pytest --user 2>/dev/null || true
                            
                            echo "=== Запуск pytest ==="
                            cd ${env.APP_SRC_DIR}
                            if [ -d "tests" ]; then
                                python3 -m pytest tests/ -v --tb=short --junitxml=../pytest-report.xml || echo "⚠ Тесты не прошли"
                            else
                                echo "⚠ Папка tests/ не найдена в репозитории приложения"
                            fi
                        """
                    } else {
                        echo "⏭ Тесты пропущены по параметру"
                    }
                }
            }
            when {
                expression { params.RUN_TESTS == true }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'pytest-report.xml'
                }
            }
        }

        // 🔹 ЭТАП 4: Очистка старых контейнеров
        stage('Cleanup Old Containers') {
            steps {
                echo "🧹 Очистка старых контейнеров"
                script {
                    if (params.CLEAN_OLD) {
                        sh """
                            echo "=== Поиск и удаление старых контейнеров ==="
                            # Фильтруем по имени приложения
                            docker ps -a --filter "name=${env.APP_NAME}" --format "{{.ID}}" | xargs -r docker rm -f || true
                            echo "✓ Очистка завершена"
                        """
                    } else {
                        echo "⏭ Очистка пропущена по параметру"
                    }
                }
            }
            when {
                expression { params.CLEAN_OLD == true }
            }
        }

        // 🔹 ЭТАП 5: Деплой приложения
        stage('Deploy Application') {
            steps {
                echo "🚀 Деплой приложения в окружение: ${env.DEPLOY_ENV}"
                script {
                    // Загружаем внешний Groovy-скрипт
                    def deployScript = load 'groovy-scripts/deploy-app.groovy'
                    
                    // ИСПРАВЛЕНИЕ: вызываем метод deploy() у загруженного объекта
                    deployScript.deploy(
                        imageName: env.DOCKER_IMAGE,
                        containerName: "${env.APP_NAME}-${env.DEPLOY_ENV}",
                        port: env.DEPLOY_PORT,
                        environment: env.DEPLOY_ENV
                    )
                }
            }
            when {
                anyOf {
                    branch 'main'
                    expression { params.DEPLOY_ENV in ['staging', 'production'] }
                }
            }
        }

        // 🔹 ЭТАП 6: Проверка доступности (Health Check)
        stage('Health Check') {
            steps {
                echo "🏥 Проверка доступности приложения"
                script {
                    sh """
                        echo "=== Проверка здоровья приложения ==="
                        MAX_RETRIES=3
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
            // Выполняется, если предыдущие этапы не упали критически
            when {
                expression { currentBuild.result == null || currentBuild.result == 'UNSTABLE' }
            }
        }

        // 🔹 ЭТАП 7: Генерация отчёта (DSL)
        stage('Generate Report') {
            steps {
                echo "📊 Генерация отчёта о сборке"
                script {
                    // Загружаем DSL-скрипт из текущего репо (pipeline)
                    def reportDSL = load 'dsl-scripts/report-generator.groovy'
                    reportDSL.generateReport(
                        jobName: env.JOB_NAME,
                        buildNumber: env.BUILD_NUMBER,
                        buildStatus: currentBuild.currentResult ?: 'SUCCESS',
                        deployEnv: env.DEPLOY_ENV,
                        outputPath: "${env.REPORT_DIR}/build-${env.BUILD_NUMBER}.json"
                    )
                }
            }
            // Отчет генерируем всегда, даже если были предупреждения
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
                to: 'k.ivanovconn@gmail.com',
                subject: "✅ SUCCESS: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: """
🎉 Сборка Jenkins прошла успешно!

📦 Приложение: ${env.APP_NAME}
🔢 Сборка: #${env.BUILD_NUMBER}
🌍 Окружение: ${env.DEPLOY_ENV}
🔗 Ссылка: ${env.BUILD_URL}

✅ Все этапы пройдены:
• Клонирование приложения: OK
• Сборка Docker: OK
• Тесты: ${params.RUN_TESTS ? 'OK' : 'PROPUщено'}
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

🔍 Возможные причины:
• Ошибка клонирования репозитория HW
• Ошибка сборки Docker-образа (нет Dockerfile)
• Не прошли автоматические тесты
• Ошибка при деплое (скрипт deploy-app.groovy)
• Health Check не прошёл

🛠 Что делать:
1. Откройте консоль сборки по ссылке выше
2. Найдите строку с ERROR
3. Исправьте ошибку
                """,
                attachLog: true
            )
        }
        unstable {
            echo "⚠️ Сборка нестабильна"
            emailext (
                to: 'k.ivanovconn@gmail.com',
                subject: "⚠️ UNSTABLE: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: "Сборка завершилась с предупреждениями (например, нет Dockerfile или тесты упали). Проверьте отчёт: ${env.BUILD_URL}"
            )
        }
    }
}