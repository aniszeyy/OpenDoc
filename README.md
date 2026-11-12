# OpenDocs

Lecteur de documents libre pour Android : DOCX, PPTX, XLSX et CSV.
Aucune permission demandee, aucun acces reseau, aucun parseur tiers :
le contenu OOXML est lu avec `java.util.zip` et SAX.

## Pourquoi le build echouait

Le depot contenait un coeur de parsing complet (modele + parseurs + tests)
**et un `AndroidManifest.xml` qui decrivait une application inexistante** :
tout le dossier `app/src/main/res` et le paquet `org.opendocs.reader.ui`
avaient disparu. Le manifeste referencait donc des ressources et des classes
absentes, et le build s'arretait des la premiere tache qui les touche.

Symptomes exacts avant correction :

```
> Task :app:processDebugResources FAILED
AAPT: error: resource xml/data_extraction_rules ... not found
AAPT: error: resource xml/backup_rules ... not found
AAPT: error: resource mipmap/ic_launcher ... not found
AAPT: error: resource string/app_name ... not found
AAPT: error: resource xml/locales_config ... not found
AAPT: error: resource mipmap/ic_launcher_round ... not found
AAPT: error: resource style/Theme.OpenDocs ... not found
AAPT: error: resource string/open_with_opendocs ... not found
AAPT: error: resource string/settings ... not found
AAPT: error: resource xml/file_paths ... not found
```

Trois causes secondaires s'ajoutaient :

1. **JDK** : `JAVA_HOME` n'etait pas defini et le `java` du PATH est un JDK 8 ;
   AGP 8.13 exige un JDK 17 minimum. Le build doit utiliser le JBR d'Android
   Studio (`C:\Program Files\Android\Android Studio\jbr`, JDK 21).
2. **Tests** : 4 tests unitaires echouaient, dont 2 sur de vrais bugs du moteur
   de formats numeriques (voir le tableau plus bas).
3. **Regles de sauvegarde** : `domain="cache"` n'est pas un domaine valide pour
   `<full-backup-content>` et `<data-extraction-rules>`. Le cache n'etant
   jamais sauvegarde, ces exclusions etaient inutiles.

## Construire

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug        # APK de developpement
.\gradlew.bat :app:assembleRelease      # APK minifie (R8 + shrinkResources)
.\gradlew.bat :app:testDebugUnitTest    # tests du coeur
.\gradlew.bat :app:lintDebug            # analyse statique
```

Sorties : `app/build/outputs/apk/debug/app-debug.apk` et
`app/build/outputs/apk/release/app-release.apk`.
Le build release est signe avec la cle de debug pour rester installable ;
remplacer `signingConfig signingConfigs.debug` par une vraie configuration
avant publication.

## Verification

| Controle | Resultat |
| --- | --- |
| `assembleDebug` | OK - `app-debug.apk`, 6,1 Mo |
| `assembleRelease` (R8 + shrinkResources) | OK - `app-release.apk`, 1,8 Mo |
| `testDebugUnitTest` | 50 tests, 0 echec |
| `lintDebug` | 0 erreur |

L'interface a ete installee et lancee sur un appareil reel (Android 16) : elle
s'affiche et le lecteur ouvre bien ses onglets, ce qui a revele le bug Android
de `XmlTree` ci-dessous, invisible depuis les tests JVM. Aucun appareil n'etant
connecte a l'environnement de developpement, les corrections ulterieures sont
validees par compilation, tests et lint — un dernier essai sur appareil reste
la meilleure verification.

## Organisation

```
app/src/main/java/org/opendocs/reader/
  App.java                  application : theme applique avant la 1re activite
  core/model/               modele commun (ParsedDocument, Block, Slide, Sheet...)
  core/parse/               parseurs DOCX / PPTX / XLSX / CSV + paquet OOXML
  core/xml/                 arbre XML SAX + preserve
  core/util/                dates Excel, formats numeriques, couleurs, encodage
  data/                     reglages et liste des documents recents
  ui/                       activites, adaptateurs et vues de rendu
  util/                     URI SAF -> fichier, partage, formats, images
```

L'interface est separee du parsing : `core/` est du Java pur, ce qui permet de
le tester sur la JVM sans emulateur.

Cote rendu, les trois formats ont leur propre vue :

- **DOCX** : `DocxBlockAdapter` (RecyclerView) - paragraphes en spans
  (gras/italique/couleur/lien), tableaux a largeurs calculees, images echantillonnees.
- **PPTX** : `SlideView` (Canvas) - les coordonnees EMU sont ramenees a la
  largeur de la vue ; geometrie, images, tableaux et texte sont dessines dans
  l'ordre de la diapositive ; `ViewPager2` fait defiler les diapositives.
- **XLSX / CSV** : `SheetView` (Canvas) - en-tete de colonnes et numeros de
  ligne figes, defilement interne avec inertie, rendu limite a la zone visible.

## Bugs corriges

| Fichier | Probleme | Correction |
| --- | --- | --- |
| [`XmlTree.java`](app/src/main/java/org/opendocs/reader/core/xml/XmlTree.java) | **Tout document OOXML refusait de s'ouvrir sur Android** : `SAXParserFactory.setXIncludeAware` n'est pas implemente par `org.apache.harmony.xml`, la classe de base JAXP levait donc `UnsupportedOperationException` (`specification "Unknown" version "0.0"`). Invisible en test unitaire : Xerces, sur la JVM, implemente la methode | appel rendu tolerant (XInclude est desactive par defaut) + test `XmlTreeTest` qui simule le parseur Android |
| `ExcelNumberFormat` | `#.##` affichait `.50` au lieu de `.5` : les zeros finaux des decimales facultatives n'etaient jamais supprimes | les `0` du masque fixent le minimum de decimales, les `#` sont tronques |
| `ExcelNumberFormat` | `[h]:mm` affichait un mois au lieu des minutes : `codeChar()` renvoyait `'['` pour un marqueur de duree | `codeChar()` lit la lettre entre crochets |
| `backup_rules.xml`, `data_extraction_rules.xml` | `domain="cache"` invalide (lint : *FullBackupContent*) | exclusions de cache supprimees, elles etaient sans effet |
| `CsvParserTest` | assertion impossible : `decode(BOM + "e" + é).substring(0,1) + "é"` ne peut pas valoir `"éé"` | assertion remplacee par `"eé"` (verifie la suppression du BOM) |
| `ExcelNumberFormatTest` | `m` isole attendu `"01"` alors que le meme fichier attend `"1/1/25"` pour `m/d/yy` | attente alignee sur la convention Excel (mois non zero-padde) |
| `ExcelNumberFormatTest` | `[$€-2] #,##0.00` attendu en suffixe alors que `$#,##0.00` est en prefixe | le symbole reste a la position ou il est ecrit, comme Excel |

## Licence

Apache 2.0 - Copyright (C) 2026 OpenDocs contributors.
