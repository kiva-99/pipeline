// cleanup-container.groovy — Groovy-скрипт для очистки старых Docker-контейнеров
// Удаляет предыдущие версии перед новым деплоем

def call(Map config) {
    def appName = config.appName ?: 'app'
    def keepLast = config.keepLast ?: 1  // Сколько последних версий сохранить
    def dryRun = config.dryRun ?: false  // Режим "только просмотр"
    
    echo "🧹 Очистка старых контейнеров: ${appName} (сохранить последних: ${keepLast})"
    
    try {
        // 1. Получаем список контейнеров по имени приложения
        def containers = sh(
            script: "docker ps -a --filter name=${appName} --format '{{.ID}} {{.Names}} {{.CreatedAt}}' | sort -k3 -r",
            returnStdout: true
        ).trim().split('\n').findAll { it }
        
        echo "📋 Найдено контейнеров: ${containers.size()}"
        
        // 2. Определяем, какие контейнеры удалить
        def toDelete = containers.drop(keepLast)
        
        if (toDelete.isEmpty()) {
            echo "✓ Нет контейнеров для удаления"
            return [deleted: 0, kept: containers.size()]
        }
        
        echo "🗑️  К удалению: ${toDelete.size()} контейнер(ов)"
        
        // 3. Выполняем удаление
        def deletedCount = 0
        toDelete.each { line ->
            def parts = line.split(' ')
            def containerId = parts[0]
            def containerName = parts[1]
            
            if (dryRun) {
                echo "🔍 [DRY RUN] Удалить: ${containerName} (${containerId})"
            } else {
                echo "🗑️  Удаляю: ${containerName}"
                sh "docker rm -f ${containerId} || true"
                deletedCount++
            }
        }
        
        // 4. Очистка образов (опционально)
        if (!dryRun && config.cleanupImages) {
            echo "🧹 Очистка неиспользуемых образов..."
            sh "docker image prune -f --filter label=app=${appName} || true"
        }
        
        echo "✓ Очистка завершена: удалено ${deletedCount} контейнер(ов)"
        return [deleted: deletedCount, kept: keepLast, dryRun: dryRun]
        
    } catch (Exception e) {
        echo "❌ Ошибка при очистке: ${e.message}"
        throw e
    }
}

return this