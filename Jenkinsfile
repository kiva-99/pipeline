// 👇 POST-ACTIONS: действия после завершения всех stages
post {
    always {
        echo "📦 Архивация артефактов..."
        archiveArtifacts(
            artifacts: "${env.REPORT_DIR}/*.json",
            allowEmptyArchive: true,
            fingerprint: true,
            onlyIfSuccessful: false
        )
        
        echo "🧹 Очистка рабочего пространства"
        cleanWs()
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
                
                📄 Артефакты (отчёты) доступны во вкладке "Artifacts" слева.
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
                • Ошибка сборки Docker-образа
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
            body: "Сборка завершилась с предупреждениями. Проверьте отчёт: ${env.BUILD_URL}"
        )
    }
}