/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core.parse;

import org.opendocs.reader.core.model.Metadata;
import org.opendocs.reader.core.xml.XmlNode;

import java.io.IOException;

/** Lecture des proprietes OPC communes : docProps/core.xml et docProps/app.xml. */
final class DocProps {

    private DocProps() {
    }

    static void read(OoxmlPackage pkg, Metadata metadata) throws IOException {
        XmlNode core = pkg.xml("docProps/core.xml");
        if (core != null) {
            metadata.title = childText(core, "title");
            metadata.subject = childText(core, "subject");
            metadata.creator = childText(core, "creator");
            metadata.description = childText(core, "description");
            metadata.keywords = childText(core, "keywords");
            metadata.lastModifiedBy = childText(core, "lastModifiedBy");
            metadata.created = childText(core, "created");
            metadata.modified = childText(core, "modified");
            metadata.revision = childText(core, "revision");
            metadata.putExtra("Categorie", childText(core, "category"));
            metadata.putExtra("Etat", childText(core, "contentStatus"));
            metadata.putExtra("Langue", childText(core, "language"));
        }
        XmlNode app = pkg.xml("docProps/app.xml");
        if (app != null) {
            metadata.application = childText(app, "Application");
            metadata.appVersion = childText(app, "AppVersion");
            metadata.putExtra("Societe", childText(app, "Company"));
            metadata.putExtra("Modele", childText(app, "Template"));
            metadata.putExtra("Temps d'edition (min)", childText(app, "TotalTime"));
            String pages = childText(app, "Pages");
            if (!pages.isEmpty() && !"0".equals(pages)) metadata.putExtra("Pages (declarees)", pages);
            String words = childText(app, "Words");
            if (!words.isEmpty() && !"0".equals(words)) metadata.putExtra("Mots (declares)", words);
            String slides = childText(app, "Slides");
            if (!slides.isEmpty() && !"0".equals(slides)) metadata.putExtra("Diapositives (declarees)", slides);
        }
    }

    private static String childText(XmlNode parent, String name) {
        XmlNode child = parent.first(name);
        return child == null ? "" : child.deepText().trim();
    }
}
