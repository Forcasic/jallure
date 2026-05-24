package io.github.forcasic.jmeter.allure

/**
 * Writes Allure attachment and result files.
 */
class AttachmentWriter {
    String reportPath

    AttachmentWriter(String reportPath) {
        this.reportPath = reportPath
    }

    void writeRequestAttachment(String uuid, String requestData, String requestType) {
        File requestFile = new File(reportPath, uuid + '-request-attachment')
        requestFile.withWriter("UTF-8") { writer -> writer.write(requestData) }
    }

    void writeResponseAttachment(String uuid, byte[] responseData, String responseType, boolean isBinary) {
        File responseFile = new File(reportPath, uuid + '-response-attachment')
        if (isBinary) {
            responseFile.withOutputStream { os -> os.write(responseData) }
        } else {
            String text = new String(responseData, java.nio.charset.StandardCharsets.UTF_8)
            responseFile.withWriter("UTF-8") { writer -> writer.write(text) }
        }
    }

    void writeResultJson(String uuid, String json) {
        File resultFile = new File(reportPath, uuid + '-result.json')
        resultFile.withWriter("UTF-8") { writer -> writer.write(json) }
    }
}
