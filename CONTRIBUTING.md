# Guide de Contribution à EvalStudio

Merci de votre intérêt pour **EvalStudio** ! Voici les directives pour contribuer efficacement au projet.

---

## 📌 Code de Conduite

Nous encourageons un environnement respectueux, bienveillant et ouvert à toutes et à tous. Merci de faire preuve de courtoisie dans vos échanges (issues, discussions, revues de code).

---

## 🐛 Signaler un bug

1. Vérifiez dans les **Issues** existantes que le bug n'a pas déjà été signalé.
2. Si le problème est inédit, ouvrez une nouvelle issue en fournissant :
   - Votre système d'exploitation et sa version (Windows 11, macOS Sequoia, Ubuntu 24.04, etc.).
   - La version de Java utilisée (`java -version`).
   - La démarche étape par étape pour reproduire le bug.
   - Les logs d'erreur (ou traces de la console / stacktrace) si disponibles.

---

## 💡 Proposer une amélioration

Les idées d'améliorations ou de nouvelles fonctionnalités sont les bienvenues. Ouvrez une **Issue** de type *Feature Request* pour discuter de la faisabilité et de l'architecture avant de vous lancer dans le développement.

---

## 🛠️ Développer sur le projet

### Prérequis

- JDK 25+
- Git

### Workflow Git (Pull Request / Merge Request)

1. **Forkez** le dépôt et clonez votre copie :
   ```bash
   git clone https://github.com/TAHRI-Khalid/EvalStudio.git
   cd EvalStudio
   ```

2. **Créez une branche dédiée** pour votre tâche :
   ```bash
   git checkout -b feature/nom-de-votre-fonctionnalite
   # ou
   git checkout -b fix/description-du-correctif
   ```

3. **Validez vos modifications** :
   - Assurez-vous que le projet compile sans erreurs :
     ```bash
     ./mvnw clean test
     ```
   - Respectez les conventions de style Java et l'architecture Spring Boot / JavaFX existante.

4. **Commitez vos changements** avec un message clair et descriptif :
   ```bash
   git commit -m "feat: ajout de l'export des statistiques en format CSV"
   ```

5. **Poussez votre branche et ouvrez une Pull Request / Merge Request**.

---

## 📄 Licence

En contribuant à ce dépôt, vous acceptez que vos contributions soient publiées sous la licence [GNU Affero General Public License v3 (AGPL v3)](LICENSE) du projet.
