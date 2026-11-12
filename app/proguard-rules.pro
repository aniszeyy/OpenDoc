# OpenDocs - regles R8 / ProGuard
# Le coeur de parsing n'utilise ni reflexion ni serialisation : aucun keep necessaire.

# SAX : l'implementation est fournie par la plateforme Android, on garde les points d'entree.
-keep class javax.xml.parsers.SAXParserFactory { *; }
-keep class org.xml.sax.** { *; }
-dontwarn javax.xml.**
-dontwarn org.xml.sax.**
-dontwarn org.w3c.dom.**

# ViewBinding genere du code reference par reflexion dans les stubs AndroidX.
-keep class androidx.viewbinding.** { *; }

# Conserver les numeros de ligne pour des rapports de crash lisibles.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Enums utilises dans les switch : garder la methode values().
-keepclassmembers enum org.opendocs.reader.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
