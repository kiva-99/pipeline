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
        // URL репозитория с ПРИЛОЖЕНИЕМ (убрал лишние пробелы в конце)
        APP_REPO_URL = 'https://github.com/kiva-99/HW.git'
        // Папка, куда склонируем приложение
        APP_SRC_DIR = 'src-app'
        
        APP_NAME = 'hw32-webapp'
        // Версия: либо параметр, либо номер сборки
        APP_VERSION = "${params.APP_VERSION_OVERRIDE ?: env.BUILD_NUMBER}"
        
        DOCKER_IMAGE = "${APP_NAME}:${APP_VERSION}"
        DEPLOY_PORT = '8090'
        REPORT_DIR = 'reports'
        
        // Присваиваем параметры переменным окружения
        DEPLOY_ENV = "${params.DEPLOY_ENV}"
    }

    stages {
        // 🔹 ЭТАП 1: Подготовка и клонирование приложения
        stage('Checkout Application') {
            steps {
                echo "🔄 Клонируем репозиторий приложения: ${env.APP_REPO_URL}"
                script {
                    // Клонируем приложение в отдельную папку
                    sh """
                        rm -rf ${env.APP_SRC_DIR} || true
                        git clone ${env.APP_REPO_URL} ${env.APP_SRC_DIR}
                        cd ${env.APP_SRC_DIR}
                        git checkout main
                        echo "✅ Приложение клонировано в папку ${env.APP_SRC_DIR}"
                    """
                }
            }
        }

        // 🔹 ЭТАП 2: Сборка приложения (Docker)
        stage('Build Application') {
            steps {
                echo "🔨 Сборка приложения ${env.APP_NAME} v${env.APP_VERSION}"
                script {
                    def dockerfilePath = "${env.APP_SRC_DIR}/hw24/Dockerfile"
                    
                    if (fileExists(dockerfilePath)) {
                        sh """
                            echo "=== Сборка Docker образа ==="
                            cd ${env.APP_SRC_DIR}/hw24
                            docker build -t ${env.DOCKER_IMAGE} .
                            echo "✓ Образ собран: ${env.DOCKER_IMAGE}"
                            
                            # Проверка, что образ действительно создан
                            docker images | grep ${env.APP_NAME}
                        """
                    } else {
                        echo "⚠ Dockerfile не найден по пути: ${dockerfilePath}"
                        error "Сборка невозможна: Dockerfile не найден!"
                    }
                }
            }
        }

        // 🔹 ЭТАП 3: Запуск тестов
        stage('Run Tests') {
            when {
                expression { params.RUN_TESTS == true }
            }
            steps {
                echo "🧪 Запуск автоматических тестов"
                script {
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
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'pytest-report.xml'
                }
            }
        }

        // 🔹 ЭТАП 4: Очистка старых контейнеров
        stage('Cleanup Old Containers') {
            when {
                expression { params.CLEAN_OLD == true }
            }
            steps {
                echo "🧹 Очистка старых контейнеров"
                script {
                    sh """
                        echo "=== Поиск и удаление старых контейнеров ==="
                        docker ps -a --filter "name=${env.APP_NAME}" --format "{{.ID}}" | xargs -r docker rm -f || true
                        echo "✓ Очистка завершена"
                    """
                }
            }
        }

        // 🔹 ЭТАП 5: Деплой приложения
        stage('Deploy Application') {
            steps {
                echo "🚀 Деплой приложения в окружение: ${env.DEPLOY_ENV}"
                script {
                    // Загружаем внешний Groovy-скрипт
                    def deployScript = load 'groovy-scripts/deploy-app.groovy'
                    
                    deployScript.deploy(
                        imageName: env.DOCKER_IMAGE,
                        containerName: "${env.APP_NAME}-${env.DEPLOY_ENV}",
                        port: env.DEPLOY_PORT,
                        environment: env.DEPLOY_ENV
                    )
                }
            }
        }

        // 🔹 ЭТАП 6: Проверка доступности (Health Check)
        // 🔥 ИСПРАВЛЕНИЕ ЗДЕСЬ: Экранирование знака $ в регулярном выражении
        stage('Health Check') {
            steps {
                echo "🏥 Проверка доступности приложения"
                script {
                    def containerName = "${env.APP_NAME}-${env.DEPLOY_ENV}"
                    sh """
                        echo "=== Проверка здоровья контейнера ${containerName} ==="
                        
                        # 1. Проверяем, запущен ли контейнер
                        # ИСПРАВЛЕНО: \\$ перед closing brace, чтобы Groovy не пытался парсить переменную
                        if ! docker ps --format '{{.Names}}' | grep -q "^${containerName}\\$"; then
                            echo "❌ Контейнер ${containerName} не найден в списке запущенных!"
                            docker ps -a --filter name=${containerName}
                            exit 1
                        fi
                        echo "✓ Контейнер запущен."

                        # 2. Пробуем сделать запрос ВНУТРИ контейнера
                        MAX_RETRIES=10
                        RETRY_COUNT=0
                        while [ \$RETRY_COUNT -lt \$MAX_RETRIES ]; do
                            if docker exec ${containerName} curl -sf http://localhost:80/ > /dev/null 2>&1; then
                                echo "✅ Приложение внутри контейнера доступно (HTTP 200 OK)!"
                                echo "📄 Ответ сервера:"
                                docker exec ${containerName} curl -s http://localhost:80/ | head -n 5
                                exit 0
                            fi
                            
                            echo "⏳ Попытка \${RETRY_COUNT}/\${MAX_RETRIES}... Ждем старта Nginx..."
                            RETRY_COUNT=\$((RETRY_COUNT + 1))
                            sleep 2
                        done
                        
                        echo "❌ Приложение не ответило внутри контейнера за время ожидания."
                        echo "📋 Логи контейнера:"
                        docker logs ${containerName} --tail 20
                        exit 1
                    """
                }
            }
        }

        // 🔹 ЭТАП 7: Генерация отчёта (DSL)
        stage('Generate Report') {
            steps {
                echo "📊 Генерация отчёта о сборке"
                script {
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
        }
    }

    // 👇 POST-ACTIONS
    post {
        always {
            echo "🧹 Очистка рабочего пространства"
            cleanWs()
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
• Ошибка при деплое (скрипт deploy-app.groovy)
• Health Check не прошёл (приложение не запустилось)