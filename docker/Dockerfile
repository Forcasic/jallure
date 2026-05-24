FROM eclipse-temurin:17-jdk-alpine

ENV JMETER_HOME="/opt/apache-jmeter/bin"
ENV JMETER_LIB="/opt/apache-jmeter/lib"
ENV TEST_FRAGMENTS_FOLDER="/mnt/jmeter/test-plan/test_fragments/"
ENV PATH="${PATH}:${JMETER_HOME}"
ENV J_VERSION="5.6.3"
ENV JMETER_DOWNLOAD_URL="https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-${J_VERSION}.tgz"
ENV YONGFA_PLUGIN_URL="https://github.com/yongfa365/yongfa365-jmeter-plugins/releases/download/3.0.1/yongfa365-jmeter-plugin-3.0.1.jar"

RUN apk add --no-cache \
    sudo \
    bash \
    curl \
    tzdata \
    jq \
    python3

# Default TZ +5:00 UTC
ENV TZ=Asia/Yekaterinburg

# Copy custom JMeter config and groovy scripts
COPY src/main/resources/jmeter /tmp/jmeter-config
COPY build/assembled-groovy/jallure.groovy /opt/jallure.groovy

# Download and install JMeter + plugins
RUN curl -L --silent ${JMETER_DOWNLOAD_URL} -k > /tmp/apache-jmeter-${J_VERSION}.tgz \
    && tar -zxvf /tmp/apache-jmeter-${J_VERSION}.tgz -C /opt \
    && mv /opt/apache-jmeter-${J_VERSION} /opt/apache-jmeter \
    && cp -rf /tmp/jmeter-config/jmeter.properties ${JMETER_HOME}/jmeter.properties \
    && sed -i -e "s%#includecontroller.prefix=%includecontroller.prefix=${TEST_FRAGMENTS_FOLDER}%g" \
        ${JMETER_HOME}/jmeter.properties \
    && curl -L --silent ${YONGFA_PLUGIN_URL} -k > /opt/apache-jmeter/lib/ext/yongfa365-jmeter-plugin.jar \
    && curl -L --silent https://repo1.maven.org/maven2/kg/apc/jmeter-plugins-json/2.7/jmeter-plugins-json-2.7.jar -k > /opt/apache-jmeter/lib/ext/jmeter-plugins-json-2.7.jar \
    && curl -L --silent https://repo1.maven.org/maven2/kg/apc/jmeter-plugins-cmn-jmeter/0.7/jmeter-plugins-cmn-jmeter-0.7.jar -k > /opt/apache-jmeter/lib/ext/jmeter-plugins-cmn-jmeter-0.7.jar \
    && curl -L --silent https://repo1.maven.org/maven2/net/sf/json-lib/json-lib/2.4/json-lib-2.4-jdk15.jar -k > /opt/apache-jmeter/lib/json-lib-2.4-jdk15.jar \
    && curl -L --silent https://repo1.maven.org/maven2/commons-lang/commons-lang/2.6/commons-lang-2.6.jar -k > /opt/apache-jmeter/lib/commons-lang-2.6.jar \
    && curl -L --silent https://repo1.maven.org/maven2/net/sf/ezmorph/ezmorph/1.0.6/ezmorph-1.0.6.jar -k > /opt/apache-jmeter/lib/ezmorph-1.0.6.jar \
    && curl -L --silent https://repo1.maven.org/maven2/commons-beanutils/commons-beanutils/1.9.4/commons-beanutils-1.9.4.jar -k > /opt/apache-jmeter/lib/commons-beanutils-1.9.4.jar \
    && curl -L --silent https://repo1.maven.org/maven2/commons-collections/commons-collections/3.2.2/commons-collections-3.2.2.jar -k > /opt/apache-jmeter/lib/commons-collections-3.2.2.jar \
    && rm -rf /tmp/*

RUN chmod +x ${JMETER_HOME}/jmeter.sh && \
    chmod +x ${JMETER_HOME}/*

WORKDIR ${JMETER_HOME}
