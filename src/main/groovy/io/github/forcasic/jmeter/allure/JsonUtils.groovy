package io.github.forcasic.jmeter.allure

/**
 * JSON serialization and content-type utilities.
 */
class JsonUtils {

    /**
     * Escapes a string for safe inclusion in a JSON value.
     */
    static String escapeJson(String input) {
        if (input == null) { return '' }
        return input
                .replace('\\', '\\\\')
                .replace('"', '\\"')
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ')
                .replace('\b', '')
                .replace('\f', '')
                .replaceAll('[\\x00-\\x1f]', '')
    }

    /**
     * Determines whether the given content type represents binary data.
     */
    static boolean isBinaryContentType(String contentType) {
        def binaryTypes = [
                'application/pdf',
                'image/',
                'application/octet-stream',
                'application/msword',
                'application/vnd.openxmlformats-officedocument',
                'application/zip',
                'audio/',
                'video/'
        ]
        return binaryTypes.any { contentType?.startsWith(it) }
    }
}
