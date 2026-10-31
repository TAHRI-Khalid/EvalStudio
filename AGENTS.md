# AGENTS.md — eval_desktop (EvalStudio)

Guide destiné aux agents d'IA et développeurs travaillant sur ce dépôt. Ce document résume l'architecture, la stack technique, les conventions et les invariants du projet.

---

## 1. Vue d'ensemble du projet

**EvalStudio** (`eval_desktop`) est une application Desktop autonome d'évaluation QCM et de correction automatisée de copies d'examens imprimées.
L'application prend en charge tout le cycle de vie :
1. **Conception des examens** (QCM à choix uniques/multiples, barème positif/négatif, mélange aléatoire des questions et options, recto/verso, export PDF avec repères de calage).
2. **Gestion des groupes et étudiants** (import Excel, matricules, logos d'établissements).
3. **Numérisation et traitement d'images** (alignement par homographie OpenCV, détection automatique des repères et cases).
4. **Classification OMR intelligente** (inférence ONNX Runtime locale pour la détection des cases cochées).
5. **Notation et statistiques** (calcul instantané des scores, détection des réponses vides/fausses, consultation de la copie annotée).
6. **Sauvegarde et restauration** (export/import complet de la base de données H2 et des fichiers médias dans un conteneur zip sécurisé avec manifest JSON).

---

## 2. Stack technique

| Composant | Technologie | Version | Rôle |
|---|---|---|---|
| **Langage** | Java (OpenJDK) | 25 | Virtual threads activés, Records, Pattern matching, Sealed classes |
| **Framework Backend** | Spring Boot | 4.1.0 | Injection de dépendances, Spring Data JPA, transactions |
| **Framework UI** | JavaFX + AtlantaFX | 25.0.4 / 2.0.1 | Interface graphique native (thème AtlantaFX Primer / Cupertino) |
| **Icônes** | Ikonli (Feather) | 12.3.1 | Pack d'icônes vectorielles pour les boutons et indicateurs |
| **Base de données** | H2 Database | 2.4.240 | Mode fichier autonome (`evaldb.mv.db`) avec Flyway/JPA |
| **Vision par ordinateur** | OpenCV (OpenPnP) | 4.9.0-0 | Détection de contours, redressement de perspective, homographie |
| **Inférence IA** | ONNX Runtime | 1.18.0 | Modèle CNN léger (`eval_model.onnx`) pour classifier les cases à cocher |
| **Génération PDF** | Apache PDFBox | 3.0.4 | Génération vectorielle des épreuves et rapports d'évaluation |
| **Tableurs** | Apache POI | 5.2.5 | Import / Export des étudiants et questions depuis/vers Excel (.xlsx) |

---

## 3. Commandes courantes

> **Important** : Le projet nécessite un JDK 25. Définir `JAVA_HOME=/usr/lib/jvm/java-25-openjdk` avant d'exécuter les commandes Maven si le JDK système par défaut est différent.

```bash
# Compilation du code source
JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./mvnw compile

# Exécution de la suite complète de tests (23 tests)
JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./mvnw test

# Exécution ciblée d'un test
JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./mvnw test -Dtest=GradeServiceTest

# Lancement de l'application en mode développement
JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./mvnw spring-boot:run

# Construction du package JAR exécutable autonome
JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./mvnw clean package -DskipTests

# Génération des paquets natifs (jpackage AppImage/Debian/Windows/macOS)
./package-native.sh
```

---

## 4. Architecture logicielle

L'arborescence des sources est située dans `src/main/java/tk/tahri/eval/desktop/` :

```
├── config/                  Configuration globale, version, chemins système, OpenCV
│   ├── AppPaths.java        Résolution des dossiers OS (~/.local/share/evalstudio ou %APPDATA%)
│   ├── AppPreferences.java   Stockage des préférences utilisateur (Java Preferences API)
│   ├── AppVersion.java      Résolution dynamique de version (v0.1.7)
│   └── OpenCvConfig.java    Chargement de la bibliothèque native OpenCV
├── model/                   Entités JPA (Evaluation, Question, QuestionOption, Student, StudentGroup...)
├── repository/              Interfaces Spring Data JPA (5 repositories)
├── service/                 Services métier applicatifs
│   ├── BackupService.java   Export streaming et restauration atomique (.evalbackup)
│   ├── EvaluationService.java Gestion et clonage d'évaluations et questions
│   ├── QuestionExcelService.java Import/export Excel des banques de questions
│   └── dto/                 DTOs de sauvegarde (BackupManifest)
├── document/                Moteur de traitement des documents et vision
│   ├── reports/             Générateurs PDF (OneColumnAssessment, TwoColumnAssessment)
│   ├── services/            Pipeline OCR/OMR et notation
│   │   ├── CheckboxClassifier.java  Inférence ONNX du modèle eval_model.onnx
│   │   ├── DocumentProcessingService.java Traitement des scans, calage et détection
│   │   ├── DocumentService.java    Coordination de la génération PDF
│   │   └── GradeService.java       Calcul des notes, pénalités et statistiques
│   └── dto/                 DTOs de traitement et TaskProgressTracker (suivi fluide)
└── ui/                      Couche présentation JavaFX
    ├── controllers/         Contrôleurs FXML (CorrectionViewController, EvaluationsViewController...)
    ├── util/                Composants réutilisables (TaskProgressDialog, ImageUploadBox, AppRestartUtils)
    └── NotificationService.java Système centralisé de notifications toast
```

Ressources (`src/main/resources/`) :
- `views/` : Vues FXML (`main-layout.fxml`, `evaluations-view.fxml`, `correction-view.fxml`, `settings-view.fxml`, etc.).
- `css/` : `style.css` (styles sur mesure et surcharges AtlantaFX).
- `model/` : Poids du modèle ONNX (`eval_model.onnx`).

---

## 5. Invariants et règles de développement

1. **Intégrité de JavaFX VirtualFlow** :
   - Dans les `TableCell` ou `CellFactory`, ne jamais faire `getTableView().getItems().get(getIndex())` sans contrôle de bornes ; préférer `getTableRow().getItem()` ou vérifier `getIndex() < getTableView().getItems().size()`.
   - Vider la sélection du `TableView` (`getSelectionModel().clearSelection()`) avant de recharger une `ObservableList` via `setAll(...)` liée à une `SortedList` / `FilteredList` pour éviter l'apparition de lignes fantômes (*phantom rows*).
   - Toujours nettoyer le texte et le graphique (`setText(null); setGraphic(null);`) quand `empty || item == null`.

2. **Base de données H2 & Fichiers de données** :
   - Les données de l'application résident dans le répertoire déterminé par `AppPaths.getDataDir()`.
   - Le fichier de base H2 est `evaldb.mv.db`.
   - Tout accès concurrent ou restauration doit respecter la libération du verrou (`evaldb.lock.db`) et utiliser `SHUTDOWN` avant le remplacement atomique des fichiers.

3. **Format de sauvegarde (.evalbackup)** :
   - Fichier ZIP contenant un fichier `manifest.json` à la racine (avec hash SHA-256, version de l'application et nombre de fichiers), le fichier `evaldb.mv.db` et le sous-dossier `upload/`.
   - Le suivi de progression de l'export/import utilise `TaskProgressTracker` avec débit continu (buffer 64 Ko) et throttling UI à 60 ms pour garantir une barre de progression fluide.

4. **Gestion de la version applicative** :
   Lors d'une montée de version, synchroniser :
   - `pom.xml` (`<version>X.Y.Z</version>`)
   - `AppVersion.java` (`DEFAULT_FALLBACK_VERSION = "X.Y.Z"`)
   - `AppVersionTest.java` (`assertEquals("vX.Y.Z", version)`)
   - `BackupService.java` (`@Value("${app.version:X.Y.Z}")`)
   - `settings-view.fxml` (`<Label fx:id="lblHeaderBadge" text="vX.Y.Z" ...>`)
   - `package-native.sh` et `package-native.ps1`
   - `.github/workflows/release.yml`

5. **Tests automatisés** :
   - Veiller à ce que l'ensemble des 23 tests JUnit passent avant tout commit (`./mvnw test`).
