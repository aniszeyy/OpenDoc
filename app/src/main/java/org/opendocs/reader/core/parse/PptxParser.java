/*
 * OpenDocs - lecteur de documents libre pour Android
 * Copyright (C) 2026 OpenDocs contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opendocs.reader.core.parse;

import org.opendocs.reader.core.model.Paragraph;
import org.opendocs.reader.core.model.ParsedDocument;
import org.opendocs.reader.core.model.Run;
import org.opendocs.reader.core.model.Shape;
import org.opendocs.reader.core.model.Slide;
import org.opendocs.reader.core.model.Table;
import org.opendocs.reader.core.util.Emu;
import org.opendocs.reader.core.util.OoxmlColor;
import org.opendocs.reader.core.xml.XmlNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parseur PPTX (PresentationML) ecrit a la main.
 *
 * <p>Couvert : taille des diapositives, ordre via sldIdLst, formes texte
 * (paragraphes, runs, puces, niveaux, alignement, autofit), geometries
 * predeterminees, remplissages et contours, images, tableaux, groupes
 * (transformation chOff/chExt), connecteurs, heritage de position depuis
 * les dispositions (layouts), fond depuis la disposition ou le masque,
 * commentaires du presentateur (notesSlides).</p>
 *
 * <p>Non couvert : animations, transitions, videos, graphiques SmartArt,
 * effets 3D, remplissages degrades complexes (aplat equivalent).</p>
 */
public final class PptxParser {

    private static final int MAX_SLIDES = 500;
    private static final int MAX_SHAPES = 600;
    private static final int MAX_IMAGES = 400;
    private static final long MAX_IMAGE_BYTES = 12L * 1024 * 1024;

    private final OoxmlPackage pkg;
    private final ParsedDocument doc;
    private int imageCount;

    private PptxParser(OoxmlPackage pkg, ParsedDocument doc) {
        this.pkg = pkg;
        this.doc = doc;
    }

    public static void parse(OoxmlPackage pkg, ParsedDocument doc) throws IOException {
        new PptxParser(pkg, doc).run();
    }

    private void run() throws IOException {
        DocProps.read(pkg, doc.metadata);
        XmlNode presentation = pkg.xml("ppt/presentation.xml");
        if (presentation == null) throw new IOException("ppt/presentation.xml absent : ce n'est pas un PPTX valide");

        XmlNode size = presentation.first("sldSz");
        if (size != null) {
            doc.slideWidthEmu = size.longAttr("cx", doc.slideWidthEmu);
            doc.slideHeightEmu = size.longAttr("cy", doc.slideHeightEmu);
        }
        Relationships presRels = Relationships.forPart(pkg, "ppt/presentation.xml");

        List<String> parts = new ArrayList<>();
        XmlNode idList = presentation.first("sldIdLst");
        if (idList != null) {
            for (XmlNode sldId : idList.children("sldId")) {
                String path = presRels.resolve("ppt/presentation.xml", sldId.attr("id"));
                if (path != null) parts.add(path);
            }
        }
        if (parts.isEmpty()) {
            parts = pkg.namesMatching("ppt/slides/slide", ".xml");
            Collections.sort(parts, new Comparator<String>() {
                @Override
                public int compare(String a, String b) {
                    return Integer.compare(trailingNumber(a), trailingNumber(b));
                }
            });
        }
        if (parts.isEmpty()) {
            doc.warn("Aucune diapositive trouvee");
            return;
        }
        int number = 1;
        for (String part : parts) {
            if (number > MAX_SLIDES) {
                doc.warn("Presentation tronquee a " + MAX_SLIDES + " diapositives");
                break;
            }
            Slide slide = parseSlide(part, number);
            if (slide != null) doc.slides.add(slide);
            number++;
        }
    }

    private static int trailingNumber(String path) {
        int end = path.lastIndexOf('.');
        String base = end < 0 ? path : path.substring(0, end);
        int start = base.length();
        while (start > 0 && Character.isDigit(base.charAt(start - 1))) start--;
        try {
            return Integer.parseInt(base.substring(start));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Diapositive                                                         */
    /* ------------------------------------------------------------------ */

    private Slide parseSlide(String part, int number) throws IOException {
        XmlNode root = pkg.xml(part);
        if (root == null) {
            doc.warn("Diapositive introuvable : " + part);
            return null;
        }
        Slide slide = new Slide();
        slide.number = number;
        Relationships slideRels = Relationships.forPart(pkg, part);

        String layoutPart = findByType(slideRels, "slideLayout", part);
        Map<String, Shape> layoutPlaceholders = new HashMap<>();
        int inheritedBackground = OoxmlColor.NONE;
        if (layoutPart != null) {
            XmlNode layout = pkg.xml(layoutPart);
            if (layout != null) {
                XmlNode layoutCSld = layout.first("cSld");
                if (layoutCSld != null) {
                    inheritedBackground = backgroundColor(layoutCSld.first("bg"));
                    XmlNode layoutTree = layoutCSld.first("spTree");
                    if (layoutTree != null) {
                        List<Shape> shapes = new ArrayList<>();
                        parseShapes(layoutTree, shapes, Relationships.forPart(pkg, layoutPart), layoutPart,
                                Transform.IDENTITY, null, false);
                        for (Shape shape : shapes) {
                            if (!shape.placeholder.isEmpty()) {
                                layoutPlaceholders.put(shape.placeholder, shape);
                                layoutPlaceholders.putIfAbsent(shape.placeholder + "#", shape);
                            }
                        }
                    }
                }
                if (inheritedBackground == OoxmlColor.NONE) {
                    String masterPart = findByType(Relationships.forPart(pkg, layoutPart), "slideMaster", layoutPart);
                    if (masterPart != null) {
                        XmlNode master = pkg.xml(masterPart);
                        if (master != null) {
                            XmlNode masterCSld = master.first("cSld");
                            if (masterCSld != null) {
                                inheritedBackground = backgroundColor(masterCSld.first("bg"));
                            }
                        }
                    }
                }
            }
        }

        XmlNode cSld = root.first("cSld");
        if (cSld == null) {
            doc.warn("Diapositive " + number + " : contenu absent");
            return slide;
        }
        int background = backgroundColor(cSld.first("bg"));
        slide.background = background != OoxmlColor.NONE ? background : inheritedBackground;

        XmlNode tree = cSld.first("spTree");
        if (tree != null) {
            parseShapes(tree, slide.shapes, slideRels, part, Transform.IDENTITY, layoutPlaceholders, true);
        }
        for (Shape shape : slide.shapes) {
            if (("title".equals(shape.placeholder) || "ctrTitle".equals(shape.placeholder)) && shape.hasText()) {
                slide.title = shape.text().trim();
                break;
            }
        }
        if (slide.title.isEmpty()) {
            for (Shape shape : slide.shapes) {
                if (shape.hasText()) {
                    slide.title = shape.text().trim();
                    break;
                }
            }
        }
        String notesPart = findByType(slideRels, "notesSlide", part);
        if (notesPart != null) slide.notes = readNotes(notesPart);
        return slide;
    }

    private static String findByType(Relationships rels, String shortType, String partPath) {
        for (Relationships.Rel rel : rels.all().values()) {
            if (shortType.equals(rel.shortType()) && !rel.external) {
                return Relationships.resolvePath(partPath, rel.target);
            }
        }
        return null;
    }

    private static int backgroundColor(XmlNode bg) {
        if (bg == null) return OoxmlColor.NONE;
        XmlNode bgPr = bg.first("bgPr");
        if (bgPr != null) {
            int solid = OoxmlColor.fromContainer(bgPr.first("solidFill"));
            if (solid != OoxmlColor.NONE) return solid;
            XmlNode grad = bgPr.first("gradFill");
            if (grad != null) {
                List<XmlNode> stops = grad.all("gs");
                if (!stops.isEmpty()) return OoxmlColor.fromContainer(stops.get(0).first("solidFill"));
            }
            XmlNode blip = bgPr.first("blipFill");
            if (blip != null) return OoxmlColor.NONE;
        }
        XmlNode bgRef = bg.first("bgRef");
        if (bgRef != null) return OoxmlColor.fromContainer(bgRef);
        return OoxmlColor.NONE;
    }

    private String readNotes(String part) throws IOException {
        XmlNode root = pkg.xml(part);
        if (root == null) return "";
        StringBuilder sb = new StringBuilder();
        for (XmlNode sp : root.all("sp")) {
            XmlNode ph = sp.find("nvSpPr/nvPr/ph");
            String type = ph == null ? "body" : ph.attr("type", "body");
            if ("sldNum".equals(type) || "dt".equals(type) || "ftr".equals(type)
                    || "hdr".equals(type) || "sldImg".equals(type)) {
                continue;
            }
            XmlNode txBody = sp.first("txBody");
            if (txBody == null) continue;
            for (XmlNode p : txBody.children("p")) {
                String text = paragraphText(p);
                if (!text.trim().isEmpty()) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(text);
                }
            }
        }
        return sb.toString().trim();
    }

    private static String paragraphText(XmlNode p) {
        StringBuilder sb = new StringBuilder();
        for (XmlNode child : p.children()) {
            if ("r".equals(child.name()) || "fld".equals(child.name())) {
                XmlNode t = child.first("t");
                if (t != null) sb.append(t.text());
            } else if ("br".equals(child.name())) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /* ------------------------------------------------------------------ */
    /* Transformation de coordonnees (groupes)                             */
    /* ------------------------------------------------------------------ */

    private static final class Transform {
        static final Transform IDENTITY = new Transform(0, 0, 1, 1, 0, 0);

        final long offsetX;
        final long offsetY;
        final double scaleX;
        final double scaleY;
        final long childX;
        final long childY;

        Transform(long offsetX, long offsetY, double scaleX, double scaleY, long childX, long childY) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.childX = childX;
            this.childY = childY;
        }

        long x(long value) {
            return offsetX + Math.round((value - childX) * scaleX);
        }

        long y(long value) {
            return offsetY + Math.round((value - childY) * scaleY);
        }

        long cx(long value) {
            return Math.round(value * scaleX);
        }

        long cy(long value) {
            return Math.round(value * scaleY);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Formes                                                              */
    /* ------------------------------------------------------------------ */

    private void parseShapes(XmlNode container, List<Shape> out, Relationships rels, String part,
                             Transform transform, Map<String, Shape> layout, boolean includeImages)
            throws IOException {
        for (XmlNode node : container.children()) {
            if (out.size() >= MAX_SHAPES) {
                doc.warn("Diapositive trop complexe : formes tronquees");
                return;
            }
            switch (node.name()) {
                case "sp":
                    out.add(parseTextShape(node, rels, part, transform, layout));
                    break;
                case "pic":
                    if (includeImages) {
                        Shape picture = parsePicture(node, rels, part, transform);
                        if (picture != null) out.add(picture);
                    }
                    break;
                case "graphicFrame": {
                    Shape table = parseGraphicFrame(node, transform);
                    if (table != null) out.add(table);
                    break;
                }
                case "cxnSp":
                    out.add(parseConnector(node, transform));
                    break;
                case "grpSp":
                    out.add(parseGroup(node, rels, part, transform, layout, includeImages));
                    break;
                case "AlternateContent": {
                    XmlNode choice = node.first("Choice");
                    XmlNode fallback = node.first("Fallback");
                    XmlNode content = choice != null ? choice : fallback;
                    if (content != null) parseShapes(content, out, rels, part, transform, layout, includeImages);
                    break;
                }
                default:
                    break;
            }
        }
    }

    private Shape parseTextShape(XmlNode node, Relationships rels, String part, Transform transform,
                                 Map<String, Shape> layout) throws IOException {
        Shape shape = new Shape();
        shape.kind = Shape.Kind.TEXT;
        XmlNode nv = node.first("nvSpPr");
        readNameAndPlaceholder(shape, nv);
        XmlNode spPr = node.first("spPr");
        readGeometry(shape, spPr, transform);

        float defaultSize = defaultSizeFor(shape.placeholder);
        XmlNode txBody = node.first("txBody");
        float fontScale = readTextBody(shape, txBody, defaultSize);

        // Heritage de position depuis la disposition si la forme ne la precise pas.
        if ((shape.cx == 0 || shape.cy == 0) && layout != null) {
            Shape inherited = layout.get(shape.placeholder);
            if (inherited == null) inherited = layout.get(shape.placeholder + "#");
            if (inherited != null) {
                shape.x = inherited.x;
                shape.y = inherited.y;
                shape.cx = inherited.cx;
                shape.cy = inherited.cy;
                if (shape.verticalAnchor == 0) shape.verticalAnchor = inherited.verticalAnchor;
            }
        }
        applyFontScale(shape, fontScale);
        if (!shape.hasText() && shape.fill != OoxmlColor.NONE) shape.kind = Shape.Kind.GEOMETRY;
        readHyperlinks(shape, txBody, rels, part);
        return shape;
    }

    private void readNameAndPlaceholder(Shape shape, XmlNode nv) {
        if (nv == null) return;
        XmlNode cNvPr = nv.first("cNvPr");
        if (cNvPr != null) shape.name = cNvPr.attr("name", "");
        XmlNode ph = nv.find("nvPr/ph");
        if (ph != null) {
            String type = ph.attr("type", "body");
            String idx = ph.attr("idx");
            shape.placeholder = idx != null ? type + ":" + idx : type;
            if ("title".equals(type) || "ctrTitle".equals(type)) shape.placeholder = type;
        }
    }

    private static float defaultSizeFor(String placeholder) {
        if (placeholder == null) return 18f;
        if (placeholder.startsWith("title") || placeholder.startsWith("ctrTitle")) return 40f;
        if (placeholder.startsWith("subTitle")) return 24f;
        if (placeholder.startsWith("ftr") || placeholder.startsWith("sldNum") || placeholder.startsWith("dt")) return 12f;
        return 22f;
    }

    private void readGeometry(Shape shape, XmlNode spPr, Transform transform) {
        if (spPr == null) return;
        XmlNode xfrm = spPr.first("xfrm");
        if (xfrm != null) {
            XmlNode off = xfrm.first("off");
            XmlNode ext = xfrm.first("ext");
            long x = off == null ? 0 : off.longAttr("x", 0);
            long y = off == null ? 0 : off.longAttr("y", 0);
            long cx = ext == null ? 0 : ext.longAttr("cx", 0);
            long cy = ext == null ? 0 : ext.longAttr("cy", 0);
            shape.x = transform.x(x);
            shape.y = transform.y(y);
            shape.cx = transform.cx(cx);
            shape.cy = transform.cy(cy);
            shape.rotationDegrees = (float) (xfrm.doubleAttr("rot", 0) / 60000.0);
            shape.flipH = xfrm.boolAttr("flipH", false);
            shape.flipV = xfrm.boolAttr("flipV", false);
        }
        XmlNode geom = spPr.first("prstGeom");
        if (geom != null) shape.geometry = geom.attr("prst", "rect");
        else if (spPr.first("custGeom") != null) shape.geometry = "rect";

        if (spPr.first("noFill") != null) {
            shape.fill = OoxmlColor.NONE;
        } else {
            int solid = OoxmlColor.fromContainer(spPr.first("solidFill"));
            if (solid != OoxmlColor.NONE) {
                shape.fill = solid;
            } else {
                XmlNode grad = spPr.first("gradFill");
                if (grad != null) {
                    List<XmlNode> stops = grad.all("gs");
                    if (stops.size() >= 2) {
                        int c1 = OoxmlColor.fromContainer(stops.get(0));
                        int c2 = OoxmlColor.fromContainer(stops.get(stops.size() - 1));
                        if (c1 == OoxmlColor.NONE) c1 = OoxmlColor.fromContainer(stops.get(0).first("solidFill"));
                        if (c2 == OoxmlColor.NONE) c2 = OoxmlColor.fromContainer(stops.get(stops.size() - 1).first("solidFill"));
                        if (c1 != OoxmlColor.NONE && c2 != OoxmlColor.NONE) {
                            shape.fill = OoxmlColor.blend(c1, c2, 0.5f);
                        }
                    } else if (stops.size() == 1) {
                        shape.fill = OoxmlColor.fromContainer(stops.get(0));
                    }
                }
            }
        }
        XmlNode ln = spPr.first("ln");
        if (ln != null) {
            if (ln.first("noFill") != null) {
                shape.lineColor = OoxmlColor.NONE;
            } else {
                shape.lineColor = OoxmlColor.fromContainer(ln.first("solidFill"));
                shape.lineWidthPt = Emu.toPoints(ln.longAttr("w", 9525));
            }
        }
    }

    /** Renvoie le facteur normAutofit (1 si absent). */
    private float readTextBody(Shape shape, XmlNode txBody, float defaultSizePt) {
        if (txBody == null) return 1f;
        shape.defaultSizePt = defaultSizePt;
        float fontScale = 1f;
        XmlNode bodyPr = txBody.first("bodyPr");
        if (bodyPr != null) {
            String anchor = bodyPr.attr("anchor", "t");
            shape.verticalAnchor = "ctr".equals(anchor) ? 1 : ("b".equals(anchor) ? 2 : 0);
            XmlNode autofit = bodyPr.first("normAutofit");
            if (autofit != null) {
                fontScale = (float) (autofit.doubleAttr("fontScale", 100000) / 100000.0);
                if (fontScale <= 0.1f) fontScale = 1f;
            }
        }
        int[] autoNumberCounters = new int[10];
        for (XmlNode p : txBody.children("p")) {
            shape.paragraphs.add(parseDrawingParagraph(p, defaultSizePt, autoNumberCounters));
        }
        return fontScale;
    }

    private static void applyFontScale(Shape shape, float scale) {
        if (scale == 1f) return;
        for (Paragraph p : shape.paragraphs) {
            for (Run r : p.runs) {
                if (r.sizePt > 0) r.sizePt *= scale;
            }
        }
        shape.defaultSizePt *= scale;
    }

    private Paragraph parseDrawingParagraph(XmlNode p, float defaultSizePt, int[] counters) {
        Paragraph paragraph = new Paragraph();
        XmlNode pPr = p.first("pPr");
        Run defaults = new Run();
        defaults.sizePt = defaultSizePt;
        int level = 0;
        boolean bullet = false;
        String bulletChar = "";
        boolean autoNumber = false;
        if (pPr != null) {
            level = pPr.intAttr("lvl", 0);
            paragraph.indentLevel = Math.max(0, Math.min(6, level));
            paragraph.align = drawingAlignment(pPr.attr("algn", "l"));
            XmlNode defRPr = pPr.first("defRPr");
            applyDrawingRunProps(defRPr, defaults, null);
            XmlNode buChar = pPr.first("buChar");
            if (buChar != null) {
                bullet = true;
                bulletChar = buChar.attr("char", "\u2022");
            }
            XmlNode buAutoNum = pPr.first("buAutoNum");
            if (buAutoNum != null) autoNumber = true;
            if (pPr.first("buNone") != null) {
                bullet = false;
                autoNumber = false;
            }
            if (pPr.attr("marL") != null && level == 0) {
                paragraph.indentLevel = (int) Math.min(6, pPr.longAttr("marL", 0) / 342900);
            }
        }
        if (bullet) {
            paragraph.bullet = true;
            paragraph.numberLabel = bulletChar.isEmpty() ? "\u2022" : bulletChar;
        } else if (autoNumber) {
            int index = Math.max(0, Math.min(9, level));
            counters[index]++;
            for (int i = index + 1; i < counters.length; i++) counters[i] = 0;
            paragraph.numberLabel = counters[index] + ".";
        }

        for (XmlNode child : p.children()) {
            switch (child.name()) {
                case "r": {
                    Run run = new Run().copyStyleFrom(defaults);
                    applyDrawingRunProps(child.first("rPr"), run, null);
                    XmlNode t = child.first("t");
                    if (t != null) run.text = t.text();
                    paragraph.add(run);
                    break;
                }
                case "fld": {
                    Run run = new Run().copyStyleFrom(defaults);
                    applyDrawingRunProps(child.first("rPr"), run, null);
                    XmlNode t = child.first("t");
                    run.text = t == null ? "" : t.text();
                    paragraph.add(run);
                    break;
                }
                case "br": {
                    Run run = new Run("\n").copyStyleFrom(defaults);
                    paragraph.runs.add(run);
                    break;
                }
                default:
                    break;
            }
        }
        return paragraph;
    }

    private void applyDrawingRunProps(XmlNode rPr, Run run, Relationships rels) {
        if (rPr == null) return;
        long sz = rPr.longAttr("sz", 0);
        if (sz > 0) run.sizePt = Emu.centiPointsToPoints(sz);
        if (rPr.attr("b") != null) run.bold = rPr.boolAttr("b", false);
        if (rPr.attr("i") != null) run.italic = rPr.boolAttr("i", false);
        String underline = rPr.attr("u");
        if (underline != null) run.underline = !"none".equals(underline);
        String strike = rPr.attr("strike");
        if (strike != null) run.strike = !"noStrike".equals(strike);
        int color = OoxmlColor.fromContainer(rPr.first("solidFill"));
        if (color != OoxmlColor.NONE) run.color = color;
        XmlNode highlight = rPr.first("highlight");
        if (highlight != null) run.highlight = OoxmlColor.fromContainer(highlight);
        XmlNode latin = rPr.first("latin");
        if (latin != null) run.font = latin.attr("typeface");
        String baseline = rPr.attr("baseline");
        if (baseline != null) {
            double value = rPr.doubleAttr("baseline", 0);
            run.superscript = value > 0;
            run.subscript = value < 0;
        }
        XmlNode link = rPr.first("hlinkClick");
        if (link != null && rels != null) {
            String target = rels.target(link.attr("id"));
            if (target != null) run.link = target;
        }
    }

    private void readHyperlinks(Shape shape, XmlNode txBody, Relationships rels, String part) {
        if (txBody == null) return;
        List<XmlNode> paragraphs = txBody.children("p");
        for (int i = 0; i < paragraphs.size() && i < shape.paragraphs.size(); i++) {
            XmlNode source = paragraphs.get(i);
            Paragraph target = shape.paragraphs.get(i);
            int runIndex = 0;
            for (XmlNode child : source.children()) {
                if (!"r".equals(child.name()) && !"fld".equals(child.name())) continue;
                XmlNode rPr = child.first("rPr");
                XmlNode hlink = rPr == null ? null : rPr.first("hlinkClick");
                if (hlink != null) {
                    String url = rels.target(hlink.attr("id"));
                    if (url != null && runIndex < target.runs.size()) target.runs.get(runIndex).link = url;
                }
                XmlNode t = child.first("t");
                if (t != null && runIndex < target.runs.size()) runIndex++;
            }
        }
    }

    private static int drawingAlignment(String value) {
        if (value == null) return Paragraph.ALIGN_LEFT;
        switch (value) {
            case "ctr":
                return Paragraph.ALIGN_CENTER;
            case "r":
                return Paragraph.ALIGN_RIGHT;
            case "just":
            case "dist":
                return Paragraph.ALIGN_JUSTIFY;
            default:
                return Paragraph.ALIGN_LEFT;
        }
    }

    private Shape parsePicture(XmlNode node, Relationships rels, String part, Transform transform) throws IOException {
        if (imageCount >= MAX_IMAGES) {
            doc.warn("Trop d'images : certaines ne sont pas affichees");
            return null;
        }
        Shape shape = new Shape();
        shape.kind = Shape.Kind.PICTURE;
        XmlNode nv = node.first("nvPicPr");
        if (nv != null) {
            XmlNode cNvPr = nv.first("cNvPr");
            if (cNvPr != null) shape.name = cNvPr.attr("name", "");
        }
        readGeometry(shape, node.first("spPr"), transform);
        XmlNode blipFill = node.first("blipFill");
        XmlNode blip = blipFill == null ? null : blipFill.first("blip");
        String relId = blip == null ? null : blip.attr("embed");
        if (relId == null) return null;
        String path = rels.resolve(part, relId);
        if (path == null || path.startsWith("http")) return null;
        byte[] data = pkg.read(path);
        if (data == null || data.length > MAX_IMAGE_BYTES) {
            doc.warn("Image ignoree : " + path);
            return null;
        }
        shape.image = data;
        int slash = path.lastIndexOf('/');
        shape.imageName = slash < 0 ? path : path.substring(slash + 1);
        imageCount++;
        return shape;
    }

    private Shape parseConnector(XmlNode node, Transform transform) {
        Shape shape = new Shape();
        shape.kind = Shape.Kind.GEOMETRY;
        shape.geometry = "line";
        XmlNode nv = node.first("nvCxnSpPr");
        if (nv != null) {
            XmlNode cNvPr = nv.first("cNvPr");
            if (cNvPr != null) shape.name = cNvPr.attr("name", "");
        }
        readGeometry(shape, node.first("spPr"), transform);
        if (shape.lineColor == OoxmlColor.NONE) shape.lineColor = 0xFF404040;
        if (shape.lineWidthPt <= 0) shape.lineWidthPt = 1f;
        return shape;
    }

    private Shape parseGroup(XmlNode node, Relationships rels, String part, Transform parent,
                             Map<String, Shape> layout, boolean includeImages) throws IOException {
        Shape group = new Shape();
        group.kind = Shape.Kind.GROUP;
        XmlNode nv = node.first("nvGrpSpPr");
        if (nv != null) {
            XmlNode cNvPr = nv.first("cNvPr");
            if (cNvPr != null) group.name = cNvPr.attr("name", "");
        }
        XmlNode grpSpPr = node.first("grpSpPr");
        Transform childTransform = parent;
        XmlNode xfrm = grpSpPr == null ? null : grpSpPr.first("xfrm");
        if (xfrm != null) {
            XmlNode off = xfrm.first("off");
            XmlNode ext = xfrm.first("ext");
            XmlNode chOff = xfrm.first("chOff");
            XmlNode chExt = xfrm.first("chExt");
            long ox = off == null ? 0 : off.longAttr("x", 0);
            long oy = off == null ? 0 : off.longAttr("y", 0);
            long cx = ext == null ? 0 : ext.longAttr("cx", 0);
            long cy = ext == null ? 0 : ext.longAttr("cy", 0);
            long chx = chOff == null ? 0 : chOff.longAttr("x", 0);
            long chy = chOff == null ? 0 : chOff.longAttr("y", 0);
            long chcx = chExt == null ? cx : chExt.longAttr("cx", cx);
            long chcy = chExt == null ? cy : chExt.longAttr("cy", cy);
            double sx = chcx == 0 ? 1 : (double) cx / chcx;
            double sy = chcy == 0 ? 1 : (double) cy / chcy;
            group.x = parent.x(ox);
            group.y = parent.y(oy);
            group.cx = parent.cx(cx);
            group.cy = parent.cy(cy);
            childTransform = new Transform(group.x, group.y, parent.scaleX * sx, parent.scaleY * sy, chx, chy);
        }
        parseShapes(node, group.children, rels, part, childTransform, layout, includeImages);
        return group;
    }

    private Shape parseGraphicFrame(XmlNode node, Transform transform) {
        XmlNode tbl = node.find("graphic/graphicData/tbl");
        if (tbl == null) return null;
        Shape shape = new Shape();
        shape.kind = Shape.Kind.TABLE;
        XmlNode nv = node.first("nvGraphicFramePr");
        if (nv != null) {
            XmlNode cNvPr = nv.first("cNvPr");
            if (cNvPr != null) shape.name = cNvPr.attr("name", "");
        }
        XmlNode xfrm = node.first("xfrm");
        if (xfrm != null) {
            XmlNode off = xfrm.first("off");
            XmlNode ext = xfrm.first("ext");
            shape.x = transform.x(off == null ? 0 : off.longAttr("x", 0));
            shape.y = transform.y(off == null ? 0 : off.longAttr("y", 0));
            shape.cx = transform.cx(ext == null ? 0 : ext.longAttr("cx", 0));
            shape.cy = transform.cy(ext == null ? 0 : ext.longAttr("cy", 0));
        }
        Table table = new Table();
        XmlNode grid = tbl.first("tblGrid");
        if (grid != null) {
            List<XmlNode> columns = grid.children("gridCol");
            if (!columns.isEmpty()) {
                float[] weights = new float[columns.size()];
                for (int i = 0; i < columns.size(); i++) weights[i] = Math.max(1, columns.get(i).longAttr("w", 1));
                table.columnWeights = weights;
            }
        }
        XmlNode tblPr = tbl.first("tblPr");
        table.firstRowIsHeader = tblPr != null && tblPr.boolAttr("firstRow", false);
        int[] counters = new int[10];
        boolean firstRow = true;
        for (XmlNode tr : tbl.children("tr")) {
            List<Table.Cell> row = new ArrayList<>();
            for (XmlNode tc : tr.children("tc")) {
                Table.Cell cell = new Table.Cell();
                cell.header = firstRow && table.firstRowIsHeader;
                XmlNode txBody = tc.first("txBody");
                if (txBody != null) {
                    for (XmlNode p : txBody.children("p")) {
                        cell.paragraphs.add(parseDrawingParagraph(p, 16f, counters));
                    }
                }
                if (cell.paragraphs.isEmpty()) cell.paragraphs.add(new Paragraph());
                XmlNode tcPr = tc.first("tcPr");
                if (tcPr != null) {
                    cell.background = OoxmlColor.fromContainer(tcPr.first("solidFill"));
                    String anchor = tcPr.attr("anchor", "t");
                    if ("ctr".equals(anchor)) cell.align = Paragraph.ALIGN_CENTER;
                }
                int span = tc.intAttr("gridSpan", 1);
                cell.colSpan = Math.max(1, span);
                row.add(cell);
            }
            if (!row.isEmpty()) table.rows.add(row);
            firstRow = false;
        }
        shape.table = table;
        return shape;
    }

    /** Resume lisible (tests / debug). */
    public static String describe(ParsedDocument doc) {
        StringBuilder sb = new StringBuilder();
        sb.append("PPTX ").append(doc.slides.size()).append(" diapositives, ")
                .append(doc.slideWidthEmu).append('x').append(doc.slideHeightEmu).append(" EMU\n");
        for (Slide s : doc.slides) {
            sb.append('#').append(s.number).append(" [").append(s.shapes.size()).append(" formes] ")
                    .append(s.title.replace('\n', ' ')).append('\n');
        }
        return sb.toString();
    }

    /** Taille par defaut d'un texte de diapositive, en points. */
    public static float defaultTextSize(String placeholder) {
        return defaultSizeFor(placeholder == null ? "" : placeholder.toLowerCase(Locale.ROOT));
    }
}
