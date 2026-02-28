// report-generator.groovy — DSL-скрипт для генерации отчётов о сборке
// Позволяет описывать параметры отчёта и генерировать JSON/HTML результаты

def generateReport(Map params) {
    def jobName = params.jobName ?: 'unknown-job'
    def buildNumber = params.buildNumber ?: '0'
    def buildStatus = params.buildStatus ?: 'UNKNOWN'
    def deployEnv = params.deployEnv ?: 'unknown'
    def outputPath = params.outputPath ?: "reports/build-${buildNumber}.json"
    def reportType = params.reportType ?: 'summary'  // summary, detailed, metrics
    def filters = params.filters ?: [:]  // Дополнительные фильтры
    
    echo "📊 Генерация отчёта: ${reportType} для ${jobName} #${buildNumber}"
    
    try {
        // 1. Сбор метрик сборки
        def metrics = collectMetrics(jobName, buildNumber, buildStatus, deployEnv)
        
        // 2. Формирование структуры отчёта
        def report = [
            report: [
                type: reportType,
                generatedAt: new Date().format('yyyy-MM-dd HH:mm:ss'),
                jenkins: [
                    job: jobName,
                    build: buildNumber.toInteger(),
                    status: buildStatus,
                    url: env.BUILD_URL ?: 'N/A'
                ],
                deployment: [
                    environment: deployEnv,
                    timestamp: new Date().time
                ],
                metrics: metrics,
                filters: filters
            ]
        ]
        
        // 3. Сериализация в JSON
        def jsonReport = groovy.json.JsonOutput.toJson(report)
        def prettyJson = groovy.json.JsonOutput.prettyPrint(jsonReport)
        
        // 4. Сохранение отчёта
        def reportDir = new File(outputPath).parent
        new File(reportDir).mkdirs()
        new File(outputPath).write(prettyJson, 'UTF-8')
        echo "✓ Отчёт сохранён: ${outputPath}"
        
        // 5. Вывод краткой информации в консоль
        echo """
📋 Краткий отчёт:
• Задача: ${jobName}
• Сборка: #${buildNumber}
• Статус: ${buildStatus}
• Окружение: ${deployEnv}
• Длительность: ${metrics.durationSeconds ?: 'N/A'} сек
• Тесты: ${metrics.testsPassed ?: 0}/${metrics.testsTotal ?: 0} пройдено
        """
        
        return [
            success: true,
            reportPath: outputPath,
            summary: report.report
        ]
        
    } catch (Exception e) {
        echo "❌ Ошибка генерации отчёта: ${e.message}"
        // Логируем стек для отладки
        e.printStackTrace()
        return [success: false, error: e.message]
    }
}

// Вспомогательный метод: сбор метрик
private def collectMetrics(String job, String build, String status, String env) {
    def metrics = [:]
    
    // Длительность сборки (если доступно)
    if (env.BUILD_DURATION) {
        metrics.durationSeconds = (env.BUILD_DURATION.toLong() / 1000).toInteger()
    }
    
    // Результаты тестов (если есть JUnit отчёт)
    try {
        def testResults = currentBuild?.testResults
        if (testResults) {
            metrics.testsTotal = testResults.total
            metrics.testsPassed = testResults.passCount
            metrics.testsFailed = testResults.failCount
            metrics.testsSkipped = testResults.skipCount
        }
    } catch (Exception e) {
        // Тесты могут отсутствовать — это нормально
        metrics.testsTotal = 0
    }
    
    // Информация об окружении
    metrics.environment = env
    metrics.buildStatus = status
    
    return metrics
}

// DSL-метод для декларативного описания отчёта
def report(Map config, Closure body) {
    def reportConfig = [
        type: config.type ?: 'summary',
        outputPath: config.outputPath ?: "reports/report-${System.currentTimeMillis()}.json",
        filters: config.filters ?: [:],
        includeMetrics: config.includeMetrics ?: true,
        includeLogs: config.includeLogs ?: false
    ]
    
    // Выполняем body для дополнительных настроек
    if (body) {
        def delegate = new ReportBuilder(reportConfig)
        body.delegate = delegate
        body.call()
    }
    
    return generateReport(reportConfig)
}

// Вспомогательный класс для DSL
class ReportBuilder {
    def config
    
    ReportBuilder(def cfg) { this.config = cfg }
    
    def type(String t) { config.type = t; return this }
    def output(String path) { config.outputPath = path; return this }
    def filter(String key, def value) { 
        config.filters[key] = value
        return this 
    }
    def includeMetrics(boolean v) { config.includeMetrics = v; return this }
    def includeLogs(boolean v) { config.includeLogs = v; return this }
}

return this