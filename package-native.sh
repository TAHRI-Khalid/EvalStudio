#!/usr/bin/env bash
set -e

# ==============================================================================
# Script de packaging natif avec jpackage pour EVAL Desktop
# ==============================================================================

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

echo "=== [1/4] Détection de jpackage et du JDK ==="

JPACKAGE_CMD=""

if command -v jpackage &>/dev/null; then
    JPACKAGE_CMD="jpackage"
elif [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/jpackage" ]; then
    JPACKAGE_CMD="$JAVA_HOME/bin/jpackage"
elif [ -x "/usr/lib/jvm/java-25-openjdk/bin/jpackage" ]; then
    JPACKAGE_CMD="/usr/lib/jvm/java-25-openjdk/bin/jpackage"
elif [ -x "/usr/lib/jvm/java-openjdk/bin/jpackage" ]; then
    JPACKAGE_CMD="/usr/lib/jvm/java-openjdk/bin/jpackage"
fi

if [ -z "$JPACKAGE_CMD" ]; then
    echo "ERREUR : 'jpackage' n'a pas été trouvé sur votre système."
    echo ""
    echo "Pour l'installer sur Fedora Linux, exécutez :"
    echo "    sudo dnf install -y java-25-openjdk-devel"
    echo ""
    echo "Sur Ubuntu / Debian :"
    echo "    sudo apt-get install -y openjdk-25-jdk"
    echo ""
    exit 1
fi

echo "Utilisation de jpackage : $($JPACKAGE_CMD --version 2>&1 | head -n 1) ($JPACKAGE_CMD)"

# Détermine le type de packaging : "app-image" (dossier binaire autonome), "rpm", ou "deb"
PACKAGE_TYPE="${1:-app-image}"

echo "=== [2/4] Compilation et création de l'archive JAR ==="
if [ -x "./mvnw" ]; then
    ./mvnw clean package -DskipTests
else
    mvn clean package -DskipTests
fi

echo "=== [3/4] Préparation des artefacts d'entrée ==="
INPUT_DIR="target/jpackage-input"
rm -rf "$INPUT_DIR"
mkdir -p "$INPUT_DIR"

cp target/eval_desktop-*.jar "$INPUT_DIR/eval_desktop.jar"

DIST_DIR="dist"
rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR"

ICON_OPT=()
if [ -f "src/main/resources/icons/app-icon.png" ]; then
    ICON_OPT=(--icon "src/main/resources/icons/app-icon.png")
fi

echo "=== [4/4] Création du binaire natif ($PACKAGE_TYPE) avec jpackage ==="

"$JPACKAGE_CMD" \
    --name "EvalStudio" \
    --app-version "1.0.0" \
    --vendor "Tahri" \
    --description "Studio d'évaluation et de correction d'épreuves (Desktop)" \
    --input "$INPUT_DIR" \
    --main-jar "eval_desktop.jar" \
    --main-class "org.springframework.boot.loader.launch.JarLauncher" \
    --dest "$DIST_DIR" \
    --type "$PACKAGE_TYPE" \
    "${ICON_OPT[@]}" \
    --java-options "--enable-native-access=ALL-UNNAMED" \
    --java-options "-Dprism.verbose=false"

echo ""
echo "=============================================================================="
echo " Packaging natif terminé avec succès !"
echo " Emplacement : $PROJECT_DIR/$DIST_DIR"
if [ "$PACKAGE_TYPE" = "app-image" ]; then
    echo " Pour lancer l'application :"
    echo "    $PROJECT_DIR/$DIST_DIR/EvalStudio/bin/EvalStudio"
fi
echo "=============================================================================="
