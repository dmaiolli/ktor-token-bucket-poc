#!/bin/bash

echo "🚀 Iniciando aplicação KTOR Token Bucket POC"
echo "============================================="
echo ""

# Verificar se Java está instalado
if ! command -v java &> /dev/null; then
    echo "❌ Java não encontrado. Por favor instale Java 17 ou superior."
    exit 1
fi

# Verificar versão do Java
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
echo "✓ Java versão: $JAVA_VERSION"

if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "⚠️  Java 17 ou superior é recomendado. Versão atual: $JAVA_VERSION"
fi

echo ""
echo "Compilando aplicação..."

# Verificar se Gradle está disponível
if command -v gradle &> /dev/null; then
    echo "✓ Usando Gradle instalado"
    gradle build --quiet
    if [ $? -eq 0 ]; then
        echo "✓ Compilação concluída com sucesso!"
        echo ""
        echo "Iniciando servidor na porta 8080..."
        gradle run
    else
        echo "❌ Erro na compilação"
        exit 1
    fi
elif [ -f "./gradlew" ] && [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "✓ Usando Gradle Wrapper"
    ./gradlew build --quiet
    if [ $? -eq 0 ]; then
        echo "✓ Compilação concluída com sucesso!"
        echo ""
        echo "Iniciando servidor na porta 8080..."
        ./gradlew run
    else
        echo "❌ Erro na compilação"
        exit 1
    fi
else
    echo ""
    echo "⚠️  Gradle não encontrado!"
    echo ""
    echo "Para executar esta POC, você precisa:"
    echo "1. Instalar Gradle: https://gradle.org/install/"
    echo "2. Ou inicializar o Gradle Wrapper:"
    echo "   gradle wrapper --gradle-version 8.5"
    echo ""
    echo "Após instalar, execute:"
    echo "   ./run.sh"
    echo ""
    echo "Ou compile manualmente:"
    echo "   gradle build && gradle run"
    exit 1
fi
