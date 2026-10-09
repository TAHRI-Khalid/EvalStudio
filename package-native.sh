#!/usr/bin/env bash
set -e

# ==============================================================================
# Script de packaging natif avec jpackage pour EvalStudio (Multi-OS)
# Support : Linux (deb, rpm, app-image), macOS (dmg, pkg, app-image), Windows (msi, exe, app-image)
# ==============================================================================

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

PACKAGE_TYPE="${1:-app-image}"
APP_VERSION="${2:-0.1.0}"

# Nettoyage de la version pour jpackage (ex: "v1.0.0" -> "1.0.0", "1.0.0-SNAPSHOT" -> "1.0.0")
APP_VERSION="${APP_VERSION#v}"
APP_VERSION="${APP_VERSION%%-*}"

echo "=== [1/4] Détection de l'OS et de jpackage ==="

OS_NAME="$(uname -s)"
case "$OS_NAME" in
    Darwin*)
        OS_FAMILY="macos"
        ;;
    MINGW*|MSYS*|CYGWIN*)
        OS_FAMILY="windows"
        ;;
    *)
        OS_FAMILY="linux"
        ;;
esac
echo "Système d'exploitation détecté : $OS_FAMILY ($OS_NAME)"

# Règle Apple macOS : CFBundleVersion impose un premier numéro strictement positif (>= 1)
JPACKAGE_VERSION="$APP_VERSION"
FIRST_NUM=$(echo "$APP_VERSION" | cut -d. -f1)
if [ "$OS_FAMILY" = "macos" ] && [ "$FIRST_NUM" = "0" ]; then
    JPACKAGE_VERSION="1.${APP_VERSION#*.}"
    echo "Avis macOS : Version interne CFBundleVersion ajustée à $JPACKAGE_VERSION (Apple exige un premier chiffre >= 1)"
fi

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
    echo "ERREUR : 'jpackage' n'a pas été trouvé."
    echo "Assurez-vous qu'un JDK 25+ est installé et que JAVA_HOME est configuré."
    exit 1
fi

echo "Utilisation de jpackage : $($JPACKAGE_CMD --version 2>&1 | head -n 1) ($JPACKAGE_CMD)"
echo "Type de paquet demandé  : $PACKAGE_TYPE"
echo "Version de l'application : $APP_VERSION"

echo "=== [2/4] Compilation et création de l'archive JAR ==="
if ! ls target/eval_desktop-*.jar 1>/dev/null 2>&1 && [ "${SKIP_BUILD:-false}" != "true" ]; then
    if [ -x "./mvnw" ]; then
        ./mvnw clean package -DskipTests
    else
        mvn clean package -DskipTests
    fi
fi

echo "=== [3/4] Préparation des artefacts d'entrée ==="
INPUT_DIR="target/jpackage-input"
rm -rf "$INPUT_DIR"
mkdir -p "$INPUT_DIR"

JAR_FILE=$(ls target/eval_desktop-*.jar 2>/dev/null | head -n 1)
if [ -z "$JAR_FILE" ]; then
    echo "ERREUR : Aucun fichier JAR trouvé dans target/"
    exit 1
fi
cp "$JAR_FILE" "$INPUT_DIR/eval_desktop.jar"

DIST_DIR="dist"
mkdir -p "$DIST_DIR"
# Nettoyage préventif des bundles existants pour éviter 'destination directory already exists'
rm -rf "$DIST_DIR/EvalStudio" "$DIST_DIR/EvalStudio.app"

ICON_OPT=()
if [ "$OS_FAMILY" = "macos" ] && [ -f "src/main/resources/icons/app-icon.icns" ]; then
    ICON_OPT=(--icon "src/main/resources/icons/app-icon.icns")
elif [ "$OS_FAMILY" = "windows" ] && [ -f "src/main/resources/icons/app-icon.ico" ]; then
    ICON_OPT=(--icon "src/main/resources/icons/app-icon.ico")
elif [ -f "src/main/resources/icons/app-icon.png" ]; then
    ICON_OPT=(--icon "src/main/resources/icons/app-icon.png")
fi

EXTRA_OPTS=()
if [ "$OS_FAMILY" = "linux" ] && [ "$PACKAGE_TYPE" != "app-image" ]; then
    EXTRA_OPTS=(--linux-shortcut --linux-menu-group "Education;Office;" --linux-package-name "evalstudio")
elif [ "$OS_FAMILY" = "macos" ]; then
    EXTRA_OPTS=(--mac-package-identifier "tk.tahri.evalstudio")
elif [ "$OS_FAMILY" = "windows" ] && [ "$PACKAGE_TYPE" != "app-image" ]; then
    EXTRA_OPTS=(--win-menu --win-shortcut --win-dir-chooser)
fi

echo "=== [4/4] Création du binaire natif ($PACKAGE_TYPE) avec jpackage ==="

"$JPACKAGE_CMD" \
    --name "EvalStudio" \
    --app-version "$JPACKAGE_VERSION" \
    --vendor "Tahri" \
    --description "Studio d evaluation et correction d epreuves (Desktop)" \
    --input "$INPUT_DIR" \
    --main-jar "eval_desktop.jar" \
    --main-class "org.springframework.boot.loader.launch.JarLauncher" \
    --dest "$DIST_DIR" \
    --type "$PACKAGE_TYPE" \
    "${ICON_OPT[@]}" \
    "${EXTRA_OPTS[@]}" \
    --java-options "--enable-native-access=ALL-UNNAMED" \
    --java-options "-Dprism.verbose=false" \
    --verbose

echo ""
echo "=============================================================================="
echo " Packaging natif terminé avec succès !"
echo " Emplacement : $PROJECT_DIR/$DIST_DIR"
echo "=============================================================================="
