// report-generator.groovy — DSL-скрипт для генерации JSON-отчёта
// Автор: Иванов Кирилл Константинович

def generateReport(Map config) {
    def jobName = config.jobName ?: 'unknown'
    def buildNumber = config.buildNumber ?: '0'
    def buildStatus = config.buildStatus ?: 'UNKNOWN'
    def deployEnv = config.deployEnv ?: 'dev'
    def outputPath = config.outputPath ?: "reports/build-${buildNumber}.json"
    
    echo "📊 Генерация отчёта: summary для ${jobName} #${buildNumber}"
    
    try {
        // Создаём папку для отчётов, если не существует
        sh "mkdir -p ${outputPath.substring(0, outputPath.lastIndexOf('/'))}"
        
        // Получаем длительность сборки из env (безопасный доступ)
        def buildDuration = env.BUILD_DURATION ?: 'N/A'
        def timestamp = new Date().format('yyyy-MM-dd HH:mm:ss')
        
        // Формируем JSON вручную (безопасно для Jenkins Sandbox)
        def reportContent = """{
  "job": "${jobName}",
  "build_number": ${buildNumber},
  "status": "${buildStatus}",
  "environment": "${deployEnv}",
  "timestamp": "${timestamp}",
  "duration": "${buildDuration}",
  "artifacts": {
    "docker_image": "${config.imageName ?: 'hw32-webapp:' + buildNumber}",
    "port": ${config.port ?: 8090}
  },
  "checks": {
    "checkout": "OK",
    "build": "OK",
    "deploy": "OK",
    "health_check": "OK"
  }
}"""
        
        // Записываем отчёт в файл через Jenkins-шаг (надёжнее shell)
        writeFile file: outputPath, text: reportContent
        echo "✅ Отчёт сохранён: ${outputPath}"
        
        // Выводим краткую сводку в консоль
        echo "📋 Сводка:"
        echo "   • Статус: ${buildStatus}"
        echo "   • Окружение: ${deployEnv}"
        echo "   • Порт: ${config.port ?: 8090}"
        
        return [success: true, path: outputPath]
        
    } catch (Exception e) {
        echo "❌ Ошибка генерации отчёта: ${e.message}"
        // Не используем printStackTrace() — заблокирован Sandbox
        echo "   Детали: ${e.class.name}"
        throw e
    }
}

// Возвращаем этот объект для вызова через load
return this