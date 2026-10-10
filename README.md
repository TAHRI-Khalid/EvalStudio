# EvalStudio (EVAL Desktop)

<p align="center">
  <img src="src/main/resources/icons/app-icon.png" alt="EvalStudio Logo" width="128" height="128">
</p>

<p align="center">
  <strong>Studio Desktop autonome d'évaluation, de conception et de correction automatisée d'épreuves académiques.</strong>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-AGPL%20v3-blue.svg" alt="License AGPL v3"></a>
  <img src="https://img.shields.io/badge/Java-25-orange.svg" alt="Java 25">
  <img src="https://img.shields.io/badge/JavaFX-25-purple.svg" alt="JavaFX 25">
  <img src="https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen.svg" alt="Spring Boot 4">
  <img src="https://img.shields.io/badge/Platform-Windows%20%7C%20macOS%20%7C%20Linux-lightgrey.svg" alt="Platforms">
</p>

---

## 📌 Présentation

**EvalStudio** est une application desktop moderne conçue pour les enseignants, formateurs et établissements d'enseignement. Elle offre une solution autonome (sans dépendance à un serveur cloud obligatoire) pour concevoir des sujets d'évaluation, générer des épreuves individualisées avec codes-barres de repérage, puis numériser et corriger automatiquement les copies grâce à la vision par ordinateur et à l'intelligence artificielle.

---

## ✨ Fonctionnalités Principales

- 📝 **Concepteur d'épreuves (*Evaluation Builder*)** : Création visuelle d'épreuves, gestion des barèmes, types de questions et mise en page.
- 🖨️ **Génération de documents PDF prêts à imprimer** : Génération automatisée avec identifiants uniques, repères visuels d'alignement et codes-barres 2D (PDF417) pour une identification infaillible des copies.
- 👁️ **Correction & Traitement d'images (OpenCV + ONNX)** :
  - Détection automatique des repères et correction de la perspective des scans.
  - Reconnaissance automatique des réponses par modèle ONNX embarqué.
- 📑 **Visualiseur PDF intégré** : Contrôle immédiat des épreuves et des copies numérisées sans quitter l'application.
- 👥 **Gestion des groupes & étudiants** : Espaces de travail par groupe, import/export des listes et notes au format Excel (`.xlsx`).
- 💾 **100% Autonome & Données locales** : Fonctionnement hors-ligne garanti avec base de données embarquée H2 en mode fichier.
- 🎨 **Interface moderne** : Interface graphique fluide construite avec JavaFX 25, AtlantaFX et le jeu d'icônes Feather.

---

## 🚀 Téléchargement & Installation

Les versions précompilées sont disponibles dans l'onglet [**Releases**](https://github.com/TAHRI-Khalid/EvalStudio/releases).

| Plateforme | Format d'installation | Instructions |
| :--- | :--- | :--- |
| **Windows** | `.msi` / `.exe` | Téléchargez l'installateur et suivez l'assistant d'installation. |
| **macOS** | `.dmg` | Glissez l'application dans le dossier `Applications`. *(Au premier lancement : clic-droit > Ouvrir)* |
| **Linux** | `.deb` / `.rpm` / `.tar.gz` | Installez via votre gestionnaire de paquets (`sudo dpkg -i ...` ou `sudo rpm -i ...`). |
| **Universel** | `.jar` | Exécutable multi-OS nécessitant Java 25 (`java -jar eval_desktop.jar`). |

---

## 🛠️ Stack Technique

- **Langage** : Java 25 (Virtual Threads)
- **Framework applicatif** : Spring Boot 4 (Data JPA, Validation)
- **Interface graphique** : JavaFX 25 + [AtlantaFX](https://github.com/mkpaz/atlantafx) (Thème) + [Ikonli](https://kordamp.org/ikonli/) (Feather Icons)
- **Base de données** : H2 Database (stockage local fichier)
- **Traitement de documents** : Apache PDFBox 3.0, Apache POI 5.4 (Excel), ZXing (PDF417)
- **Vision par ordinateur & IA** : OpenCV (OpenPnP 4.9), ONNX Runtime 1.20

---

## 💻 Démarrage Développeur

### Prérequis

- **JDK 25** ou supérieur installé ([Adoptium Temurin](https://adoptium.net/) ou OpenJDK).
- **Git** et **Maven** (le wrapper `./mvnw` est inclus dans le projet).

### Cloner le projet

```bash
git clone https://github.com/TAHRI-Khalid/EvalStudio.git
cd EvalStudio
```

### Lancer l'application en mode développement

Sous Linux / macOS :
```bash
./mvnw clean compile javafx:run
```

Sous Windows (PowerShell / CMD) :
```cmd
mvnw.cmd clean compile javafx:run
```

### Compiler le package JAR

```bash
./mvnw clean package -DskipTests
```
Le fichier JAR exécutable est généré dans `target/eval_desktop-0.1.1.jar`.

---

## 📦 Création des paquets natifs avec `jpackage`

Le projet inclut un script automatisé pour créer un binaire autonome contenant le JRE minimal :

```bash
# Générer un bundle applicatif portable
./package-native.sh app-image

# Générer un paquet Debian (.deb)
./package-native.sh deb

# Générer un paquet RPM (.rpm)
./package-native.sh rpm
```

Les livrables sont placés dans le répertoire `dist/`.

---

## 🤝 Contribuer

Les contributions, signalements de bugs et suggestions sont les bienvenus !
Consultez le guide [CONTRIBUTING.md](CONTRIBUTING.md) pour découvrir comment configurer votre environnement et proposer vos modifications.

---

## 📄 Licence
 
Ce projet est distribué sous la licence [GNU Affero General Public License v3.0 (AGPL v3)](LICENSE).
Consultez le fichier `LICENSE` pour connaître les termes complets de redistribution et de réciprocité.
