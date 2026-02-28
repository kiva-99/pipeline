// deploy-app.groovy — Groovy-скрипт для деплоя Docker-контейнера
// Используется в Jenkins Pipeline для автоматического развёртывания

def call(Map config) {
    def imageName = config.imageName ?: 'app:latest'
    def containerName = config.containerName ?: 'app-container'
    def port = config.port ?: '8080'
    def environment = config.environment ?: 'staging'
    
    echo "🚀 Деплой: ${imageName} → ${containerName} (порт ${port}, env: ${environment})"
    
    try {
        // 1. Остановка и удаление старого контейнера (если есть)
        echo "🔄 Остановка существующего контейнера..."
        sh "docker stop ${containerName} 2>/dev/null || true"
        sh "docker rm ${containerName} 2>/dev/null || true"
        
        // 2. Запуск нового контейнера
        echo "▶️  Запуск нового контейнера..."
        sh """
            docker run -d \\
                --name ${containerName} \\
                --restart unless-stopped \\
                -p ${port}:8080 \\
                -e APP_ENV=${environment} \\
                -e BUILD_NUMBER=${env.BUILD_NUMBER ?: 'unknown'} \\
                ${imageName}
        """
        
        // 3. Проверка, что контейнер запущен
        echo "✅ Проверка статуса контейнера..."
        sh "docker ps --filter name=${containerName} --format '{{.Status}}' | grep -q 'Up' || exit 1"
        
        echo "✓ Деплой завершён успешно!"
        return [success: true, container: containerName, port: port]
        
    } catch (Exception e) {
        echo "❌ Ошибка при деплое: ${e.message}"
        // Логируем ошибку для отладки
        sh "docker logs ${containerName} --tail 50 || true"
        throw e
    }
}

// Экспорт функции для использования в Pipeline
return this