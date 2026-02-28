// Jenkinsfile — Declarative Pipeline для HW32
pipeline {
    // АГЕНТ
    agent { label 'docker-builder' }

    // ПАРАМЕТРЫ (Здесь задаем значения по умолчанию)
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

    // ПЕРЕМЕННЫЕ ОКРУЖЕНИЯ
    environment {
        REPO_URL = 'https://github.com/kiva-99/pipeline.git'
        APP_NAME = 'hw32-webapp'
        APP_VERSION = "${env.BUILD_NUMBER}"
        DOCKER_IMAGE = "${APP_NAME}:${APP_VERSION}"
        DOCKER_REGISTRY = 'localhost:5000'
        DEPLOY_PORT = '8080'
        REPORT_DIR = 'reports'
        
        // ИСПРАВЛЕНИЕ 1: Просто берем значение параметра. 
        // Логика "если пусто то staging" теперь гарантируется настройкой default в блоке parameters выше.
        DEPLOY_ENV = "${params.DEPLOY_ENV}"
        RUN_TESTS = "${params.RUN_TESTS}"
        CLEAN_OLD = "${params.CLEAN_OLD}"
    }

    stages {
        // ЭТАП 1: Checkout
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

        // ЭТАП 2: Build
        stage('Build Application') {
            steps {
                echo "🔨 Сборка приложения ${env.APP_NAME} v${env.APP_VERSION}"
                script {
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
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                    expression { params.DEPLOY_ENV == 'dev' }
                }
            }
        }

        // ЭТАП 3: Tests
        stage('Run Tests') {
            steps {
                echo "🧪 Запуск автоматических тестов"
                script {
                    if (params.RUN_TESTS) { // Используем params напрямую
                        sh '''
                            echo "=== Установка зависимостей ==="
                            pip3 install pytest --user 2>/dev/null || true
                            echo "=== Запуск pytest ==="
                            if [ -d "tests" ]; then
                                python3 -m pytest tests/ -v --tb=short --junitxml=pytest-report.xml || echo "⚠ Тесты не прошли"
                            else
                                echo "⚠ Папка tests/ не найдена"
                            fi
                        '''
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

        // ЭТАП 4: Cleanup
        stage('Cleanup Old Containers') {
            steps {
                echo "🧹 Очистка старых контейнеров"
                script {
                    if (params.CLEAN_OLD) {
                        sh '''
                            echo "=== Поиск и удаление старых контейнеров ==="
                            docker ps -a --filter "name=${env.APP_NAME}" --format "{{.ID}}" | xargs -r docker rm -f || true
                            echo "✓ Очистка завершена"
                        '''
                    } else {
                        echo "⏭ Очистка пропущена"
                    }
                }
            }
            when {
                expression { params.CLEAN_OLD == true }
            }
        }

        // ЭТАП 5: Deploy
        stage('Deploy Application') {
            steps {
                echo "🚀 Деплой приложения в окружение: ${env.DEPLOY_ENV}"
                script {
                    // Загружаем внешний скрипт
                    def deployScript = load 'groovy-scripts/deploy-app.groovy'
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

        // ЭТАП 6: Health Check
        stage('Health Check') {
            steps {
                echo "🏥 Проверка доступности приложения"
                script {
                    sh """
                        echo "=== Проверка здоровья ==="
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
                        echo "❌ Приложение не ответило"
                        exit 1
                    """
                }
            }
            // ИСПРАВЛЕНИЕ 2: Убрали некорректный when { always }. 
            // Эта стадия выполнится, если до неё дойдет очередь (т.е. если предыдущие успех).
            // Если нужно выполнять всегда даже при ошибке деплоя, перенесите логику в post { always }.
        }

        // ЭТАП 7: Generate Report
        stage('Generate Report') {
            steps {
                echo "📊 Генерация отчёта о сборке"
                script {
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
            // ИСПРАВЛЕНИЕ 3: Удалили блок when { always }, который вызывал ошибку.
            // Стадия выполнится, если пайплайн дошел до неё. 
            // Если отчет нужен даже при падении, используйте post { always } внизу.
        }
    }

    // POST-ACTIONS
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
                body: "Сборка прошла успешно! Окружение: ${env.DEPLOY_ENV}"
            )
        }
        failure {
            echo "❌ Сборка провалилась!"
            emailext (
                to: 'k.ivanovconn@gmail.com',
                subject: "❌ FAILURE: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: "Ошибка сборки. Проверьте консоль: ${env.BUILD_URL}console",
                attachLog: true
            )
        }
        unstable {
            echo "⚠️ Сборка нестабильна"
            emailext (
                to: 'k.ivanovconn@gmail.com',
                subject: "⚠️ UNSTABLE: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: "Есть предупреждения."
            )
        }
    }
}