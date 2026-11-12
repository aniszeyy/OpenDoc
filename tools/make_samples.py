#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenDocs - generateur de documents d'exemple (DOCX / PPTX / XLSX / CSV).

Aucune dependance externe : un fichier OOXML est une archive ZIP contenant
du XML, on les ecrit donc a la main. Les fichiers produits servent a la fois
de jeu d'essai pour l'application et de fixtures pour les tests unitaires.

Usage :
    python3 tools/make_samples.py [--out samples]
"""

import argparse
import os
import struct
import zipfile
import zlib

NS_W = 'xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"'
NS_R = 'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"'
NS_A = 'xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"'
NS_P = 'xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"'
NS_S = 'xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"'
XML_DECL = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'

REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
CT_NS = "http://schemas.openxmlformats.org/package/2006/content-types"


# --------------------------------------------------------------------------- #
# Helpers                                                                      #
# --------------------------------------------------------------------------- #
def esc(text):
    """Echappe les caracteres XML reserves (les textes d'exemple contiennent des &)."""
    return (str(text).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))


def png_bytes(width=96, height=64):
    """Petit PNG degrade (ecrit a la main : signature + IHDR + IDAT + IEND)."""

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    raw = b""
    for y in range(height):
        raw += b"\x00"
        for x in range(width):
            raw += bytes((int(255 * x / width), int(255 * y / height), 160))
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def core_props(title, creator="OpenDocs", subject="Document d'exemple"):
    return XML_DECL + (
        '<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"'
        ' xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/"'
        ' xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">'
        f"<dc:title>{title}</dc:title><dc:subject>{subject}</dc:subject>"
        f"<dc:creator>{creator}</dc:creator><cp:lastModifiedBy>OpenDocs</cp:lastModifiedBy>"
        '<dcterms:created xsi:type="dcterms:W3CDTF">2026-01-15T09:30:00Z</dcterms:created>'
        '<dcterms:modified xsi:type="dcterms:W3CDTF">2026-02-02T14:05:00Z</dcterms:modified>'
        "<cp:revision>3</cp:revision></cp:coreProperties>"
    )


def app_props(application, extra=""):
    return XML_DECL + (
        '<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties">'
        f"<Application>{application}</Application><AppVersion>16.0000</AppVersion>"
        f"<Company>OpenDocs</Company>{extra}</Properties>"
    )


def rels(entries):
    body = "".join(
        f'<Relationship Id="{rid}" Type="{rtype}" Target="{target}"'
        + (' TargetMode="External"' if external else "") + "/>"
        for rid, rtype, target, external in entries
    )
    return XML_DECL + f'<Relationships xmlns="{REL_NS}">{body}</Relationships>'


def content_types(defaults, overrides):
    body = "".join(f'<Default Extension="{ext}" ContentType="{ct}"/>' for ext, ct in defaults)
    body += "".join(f'<Override PartName="{part}" ContentType="{ct}"/>' for part, ct in overrides)
    return XML_DECL + f'<Types xmlns="{CT_NS}">{body}</Types>'


def write_zip(path, parts):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        for name, data in parts:
            z.writestr(name, data)
    print(f"  {path}  ({os.path.getsize(path)} octets)")


# --------------------------------------------------------------------------- #
# DOCX                                                                         #
# --------------------------------------------------------------------------- #
def make_docx(out):
    R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    def run(text, bold=False, italic=False, underline=False, color=None, size=None, highlight=None):
        props = ""
        if bold:
            props += "<w:b/>"
        if italic:
            props += "<w:i/>"
        if underline:
            props += '<w:u w:val="single"/>'
        if color:
            props += f'<w:color w:val="{color}"/>'
        if size:
            props += f'<w:sz w:val="{size}"/>'
        if highlight:
            props += f'<w:highlight w:val="{highlight}"/>'
        rpr = f"<w:rPr>{props}</w:rPr>" if props else ""
        return f'<w:r>{rpr}<w:t xml:space="preserve">{esc(text)}</w:t></w:r>'

    def para(runs, style=None, align=None, num=None, ilvl=0):
        ppr = ""
        if style:
            ppr += f'<w:pStyle w:val="{style}"/>'
        if align:
            ppr += f'<w:jc w:val="{align}"/>'
        if num:
            ppr += f'<w:numPr><w:ilvl w:val="{ilvl}"/><w:numId w:val="{num}"/></w:numPr>'
        ppr = f"<w:pPr>{ppr}</w:pPr>" if ppr else ""
        return f"<w:p>{ppr}{runs}</w:p>"

    drawing = (
        "<w:r><w:drawing><wp:inline "
        'xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" '
        'distT="0" distB="0" distL="0" distR="0">'
        '<wp:extent cx="1828800" cy="1219200"/>'
        '<wp:docPr id="1" name="Illustration" descr="Degrade de demonstration"/>'
        "<a:graphic " + NS_A + "><a:graphicData "
        'uri="http://schemas.openxmlformats.org/drawingml/2006/picture">'
        '<pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">'
        '<pic:nvPicPr><pic:cNvPr id="1" name="image1.png"/><pic:cNvPicPr/></pic:nvPicPr>'
        f'<pic:blipFill><a:blip r:embed="rId10"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>'
        '<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="1828800" cy="1219200"/></a:xfrm>'
        '<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>'
        "</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>"
    )

    table = (
        "<w:tbl><w:tblPr><w:tblW w:w=\"8640\" w:type=\"dxa\"/>"
        '<w:tblBorders><w:top w:val="single" w:sz="4"/><w:left w:val="single" w:sz="4"/>'
        '<w:bottom w:val="single" w:sz="4"/><w:right w:val="single" w:sz="4"/>'
        '<w:insideH w:val="single" w:sz="4"/><w:insideV w:val="single" w:sz="4"/></w:tblBorders></w:tblPr>'
        '<w:tblGrid><w:gridCol w:w="2880"/><w:gridCol w:w="2880"/><w:gridCol w:w="2880"/></w:tblGrid>'
        + "<w:tr><w:trPr><w:tblHeader/></w:trPr>"
        + "".join(
            '<w:tc><w:tcPr><w:tcW w:w="2880" w:type="dxa"/>'
            '<w:shd w:val="clear" w:fill="DCE6F1"/></w:tcPr>'
            + para(run(h, bold=True)) + "</w:tc>"
            for h in ("Produit", "Quantite", "Prix")
        )
        + "</w:tr>"
        + "".join(
            "<w:tr>"
            + "".join(f"<w:tc><w:tcPr><w:tcW w:w=\"2880\" w:type=\"dxa\"/></w:tcPr>{para(run(v))}</w:tc>"
                      for v in row)
            + "</w:tr>"
            for row in (("Café arabica", "12", "8,50 €"), ("Thé vert", "7", "5,20 €"))
        )
        + "</w:tbl>"
    )

    body = (
        para(run("Rapport de demonstration OpenDocs"), style="Heading1")
        + para(run("Ce document exerce les fonctionnalites du parseur DOCX : "
                   "styles, titres, listes, tableaux, images et liens.")
               + run(" Texte en gras.", bold=True)
               + run(" Texte en italique.", italic=True)
               + run(" Texte souligne.", underline=True)
               + run(" Texte rouge.", color="C00000")
               + run(" Surligne.", highlight="yellow"))
        + para(run("Section 1 : liste a puces"), style="Heading2")
        + para(run("Premier element"), num=1)
        + para(run("Deuxieme element"), num=1)
        + para(run("Sous-element"), num=1, ilvl=1)
        + para(run("Section 2 : liste numerotee"), style="Heading2")
        + para(run("Etape une"), num=2)
        + para(run("Etape deux"), num=2)
        + para(run("Etape trois"), num=2)
        + para(run("Section 3 : tableau"), style="Heading2")
        + table
        + para(run("Section 4 : image et lien"), style="Heading2")
        + para(drawing)
        + para('<w:hyperlink r:id="rId11">'
               + run("Projet OpenDocs sur example.org", color="0563C1", underline=True)
               + "</w:hyperlink>")
        + para(run("Fin du document.", italic=True), align="center")
    )

    document = XML_DECL + f"<w:document {NS_W} {NS_R}><w:body>{body}" \
        '<w:sectPr><w:pgSz w:w="11906" w:h="16838"/>' \
        '<w:pgMar w:top="1417" w:right="1417" w:bottom="1417" w:left="1417"/></w:sectPr></w:body></w:document>'

    styles = XML_DECL + f"<w:styles {NS_W}>" \
        "<w:docDefaults><w:rPrDefault><w:rPr><w:sz w:val=\"22\"/>" \
        '<w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/></w:rPr></w:rPrDefault>' \
        '<w:pPrDefault><w:pPr><w:spacing w:after="120"/></w:pPr></w:pPrDefault></w:docDefaults>' \
        '<w:style w:type="paragraph" w:styleId="Normal" w:default="1"><w:name w:val="Normal"/></w:style>' \
        '<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/>' \
        '<w:basedOn w:val="Normal"/><w:pPr><w:outlineLvl w:val="0"/><w:spacing w:before="240" w:after="120"/></w:pPr>' \
        '<w:rPr><w:b/><w:sz w:val="40"/><w:color w:val="1F497D"/></w:rPr></w:style>' \
        '<w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/>' \
        '<w:basedOn w:val="Normal"/><w:pPr><w:outlineLvl w:val="1"/></w:pPr>' \
        '<w:rPr><w:b/><w:sz w:val="28"/><w:color w:val="2E74B5"/></w:rPr></w:style>' \
        "</w:styles>"

    numbering = XML_DECL + f"<w:numbering {NS_W}>" \
        '<w:abstractNum w:abstractNumId="0">' \
        '<w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="&#8226;"/></w:lvl>' \
        '<w:lvl w:ilvl="1"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="&#9702;"/></w:lvl>' \
        "</w:abstractNum>" \
        '<w:abstractNum w:abstractNumId="1">' \
        '<w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/></w:lvl>' \
        '<w:lvl w:ilvl="1"><w:start w:val="1"/><w:numFmt w:val="lowerLetter"/><w:lvlText w:val="%2)"/></w:lvl>' \
        "</w:abstractNum>" \
        '<w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>' \
        '<w:num w:numId="2"><w:abstractNumId w:val="1"/></w:num>' \
        "</w:numbering>"

    parts = [
        ("[Content_Types].xml", content_types(
            [("rels", "application/vnd.openxmlformats-package.relationships+xml"),
             ("xml", "application/xml"), ("png", "image/png")],
            [("/word/document.xml",
              "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"),
             ("/word/styles.xml",
              "application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"),
             ("/word/numbering.xml",
              "application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"),
             ("/docProps/core.xml", "application/vnd.openxmlformats-package.core-properties+xml"),
             ("/docProps/app.xml",
              "application/vnd.openxmlformats-officedocument.extended-properties+xml")])),
        ("_rels/.rels", rels([
            ("rId1", R + "/officeDocument", "word/document.xml", False),
            ("rId2", "http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties",
             "docProps/core.xml", False),
            ("rId3", R + "/extended-properties", "docProps/app.xml", False)])),
        ("word/document.xml", document),
        ("word/styles.xml", styles),
        ("word/numbering.xml", numbering),
        ("word/_rels/document.xml.rels", rels([
            ("rId10", R + "/image", "media/image1.png", False),
            ("rId11", R + "/hyperlink", "https://example.org/opendocs", True)])),
        ("word/media/image1.png", png_bytes()),
        ("docProps/core.xml", core_props("Rapport de demonstration")),
        ("docProps/app.xml", app_props("OpenDocs sample generator", "<Pages>1</Pages><Words>120</Words>")),
    ]
    write_zip(os.path.join(out, "exemple.docx"), parts)


# --------------------------------------------------------------------------- #
# PPTX                                                                         #
# --------------------------------------------------------------------------- #
def text_shape(sid, name, ph, x, y, cx, cy, paragraphs, fill=None, geom="rect", anchor="t"):
    ph_xml = f'<p:ph type="{ph}"/>' if ph else ""
    fill_xml = f'<a:solidFill><a:srgbClr val="{fill}"/></a:solidFill>' if fill else "<a:noFill/>"
    return (
        f"<p:sp><p:nvSpPr><p:cNvPr id=\"{sid}\" name=\"{name}\"/>"
        f"<p:cNvSpPr><a:spLocks noGrp=\"1\"/></p:cNvSpPr><p:nvPr>{ph_xml}</p:nvPr></p:nvSpPr>"
        f"<p:spPr><a:xfrm><a:off x=\"{x}\" y=\"{y}\"/><a:ext cx=\"{cx}\" cy=\"{cy}\"/></a:xfrm>"
        f'<a:prstGeom prst="{geom}"><a:avLst/></a:prstGeom>{fill_xml}</p:spPr>'
        f'<p:txBody><a:bodyPr anchor="{anchor}"/><a:lstStyle/>{paragraphs}</p:txBody></p:sp>'
    )


def dml_para(text, size=2400, bold=False, color="000000", bullet=False, align=None, level=0):
    ppr = ""
    attrs = f' lvl="{level}"' if level else ""
    if align:
        attrs += f' algn="{align}"'
    bu = '<a:buChar char="&#8226;"/>' if bullet else "<a:buNone/>"
    ppr = f"<a:pPr{attrs}>{bu}</a:pPr>"
    b = ' b="1"' if bold else ""
    return (f"<a:p>{ppr}<a:r><a:rPr lang=\"fr-FR\" sz=\"{size}\"{b} dirty=\"0\">"
            f'<a:solidFill><a:srgbClr val="{color}"/></a:solidFill></a:rPr>'
            f"<a:t>{esc(text)}</a:t></a:r></a:p>")


def slide_xml(shapes, background=None):
    bg = ""
    if background:
        bg = (f"<p:bg><p:bgPr><a:solidFill><a:srgbClr val=\"{background}\"/></a:solidFill>"
              "<a:effectLst/></p:bgPr></p:bg>")
    return XML_DECL + (
        f"<p:sld {NS_P} {NS_R} {NS_A}><p:cSld>{bg}<p:spTree>"
        '<p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>'
        '<p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/>'
        '<a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>'
        + shapes + "</p:spTree></p:cSld>"
        '<p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>'
    )


def make_pptx(out):
    R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    W, H = 12192000, 6858000  # 16:9

    slide1 = slide_xml(
        text_shape(2, "Titre", "ctrTitle", 838200, 2000000, 10515600, 1500000,
                   dml_para("OpenDocs", size=5400, bold=True, color="1F497D", align="ctr"), anchor="ctr")
        + text_shape(3, "Sous-titre", "subTitle", 838200, 3700000, 10515600, 900000,
                     dml_para("Lecteur libre de documents pour Android", size=2800,
                              color="404040", align="ctr")),
        background="F2F6FA")

    picture = (
        '<p:pic><p:nvPicPr><p:cNvPr id="7" name="Illustration"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr>'
        f'<p:blipFill><a:blip r:embed="rId20"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>'
        '<p:spPr><a:xfrm><a:off x="8300000" y="2200000"/><a:ext cx="3000000" cy="2000000"/></a:xfrm>'
        '<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr></p:pic>'
    )
    slide2 = slide_xml(
        text_shape(2, "Titre", "title", 838200, 400000, 10515600, 1100000,
                   dml_para("Formats supportes", size=4000, bold=True, color="1F497D"))
        + text_shape(3, "Contenu", "body", 838200, 1800000, 6800000, 3800000,
                     dml_para("DOCX : texte, styles, tableaux", bullet=True)
                     + dml_para("PPTX : diapositives et notes", bullet=True)
                     + dml_para("XLSX : feuilles et formats", bullet=True)
                     + dml_para("CSV : separateurs multiples", bullet=True))
        + text_shape(8, "Encadre", None, 8300000, 4600000, 3000000, 1200000,
                     dml_para("100 % hors ligne", size=2000, bold=True, color="FFFFFF", align="ctr"),
                     fill="2E74B5", geom="roundRect", anchor="ctr")
        + picture)

    rows = (("Format", "Lecture", "Export"),
            ("DOCX", "Oui", "TXT / HTML"),
            ("XLSX", "Oui", "CSV / HTML"))
    table_rows = ""
    for i, row in enumerate(rows):
        cells = ""
        for value in row:
            cells += ("<a:tc><a:txBody><a:bodyPr/><a:lstStyle/>"
                      + dml_para(value, size=1800, bold=(i == 0))
                      + "</a:txBody><a:tcPr/></a:tc>")
        table_rows += f'<a:tr h="600000">{cells}</a:tr>'
    table = (
        '<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id="9" name="Tableau"/>'
        '<p:cNvGraphicFramePr/><p:nvPr/></p:nvGraphicFramePr>'
        '<p:xfrm><a:off x="838200" y="1900000"/><a:ext cx="9000000" cy="1800000"/></p:xfrm>'
        '<a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/table">'
        '<a:tbl><a:tblPr firstRow="1"/><a:tblGrid>'
        '<a:gridCol w="3000000"/><a:gridCol w="3000000"/><a:gridCol w="3000000"/></a:tblGrid>'
        + table_rows + "</a:tbl></a:graphicData></a:graphic></p:graphicFrame>"
    )
    slide3 = slide_xml(
        text_shape(2, "Titre", "title", 838200, 400000, 10515600, 1100000,
                   dml_para("Tableau et notes", size=4000, bold=True, color="1F497D"))
        + table)

    notes = XML_DECL + (
        f"<p:notes {NS_P} {NS_R} {NS_A}><p:cSld><p:spTree>"
        '<p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>'
        '<p:grpSpPr/>'
        '<p:sp><p:nvSpPr><p:cNvPr id="2" name="Notes"/><p:cNvSpPr/><p:nvPr>'
        '<p:ph type="body" idx="1"/></p:nvPr></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:lstStyle/>'
        + dml_para("Insister sur le fait qu'aucune permission n'est requise.", size=1400)
        + dml_para("Aucune donnee ne quitte l'appareil.", size=1400)
        + "</p:txBody></p:sp></p:spTree></p:cSld></p:notes>"
    )

    layout = XML_DECL + (
        f"<p:sldLayout {NS_P} {NS_R} {NS_A} type=\"title\"><p:cSld><p:spTree>"
        '<p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/>'
        + text_shape(2, "Titre", "title", 838200, 400000, 10515600, 1100000, dml_para("Titre"))
        + text_shape(3, "Contenu", "body", 838200, 1800000, 10515600, 4000000, dml_para("Texte"))
        + "</p:spTree></p:cSld></p:sldLayout>"
    )
    master = XML_DECL + (
        f"<p:sldMaster {NS_P} {NS_R} {NS_A}><p:cSld>"
        '<p:bg><p:bgPr><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill><a:effectLst/></p:bgPr></p:bg>'
        '<p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>'
        "<p:grpSpPr/></p:spTree></p:cSld>"
        '<p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rId1"/></p:sldLayoutIdLst></p:sldMaster>'
    )

    presentation = XML_DECL + (
        f"<p:presentation {NS_P} {NS_R} {NS_A}>"
        '<p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId1"/></p:sldMasterIdLst>'
        '<p:sldIdLst><p:sldId id="256" r:id="rId2"/><p:sldId id="257" r:id="rId3"/>'
        '<p:sldId id="258" r:id="rId4"/></p:sldIdLst>'
        f'<p:sldSz cx="{W}" cy="{H}"/><p:notesSz cx="{H}" cy="{W}"/></p:presentation>'
    )

    ct = content_types(
        [("rels", "application/vnd.openxmlformats-package.relationships+xml"),
         ("xml", "application/xml"), ("png", "image/png")],
        [("/ppt/presentation.xml",
          "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"),
         ("/ppt/slides/slide1.xml", "application/vnd.openxmlformats-officedocument.presentationml.slide+xml"),
         ("/ppt/slides/slide2.xml", "application/vnd.openxmlformats-officedocument.presentationml.slide+xml"),
         ("/ppt/slides/slide3.xml", "application/vnd.openxmlformats-officedocument.presentationml.slide+xml"),
         ("/ppt/slideLayouts/slideLayout1.xml",
          "application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"),
         ("/ppt/slideMasters/slideMaster1.xml",
          "application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"),
         ("/ppt/notesSlides/notesSlide3.xml",
          "application/vnd.openxmlformats-officedocument.presentationml.notesSlide+xml"),
         ("/docProps/core.xml", "application/vnd.openxmlformats-package.core-properties+xml"),
         ("/docProps/app.xml", "application/vnd.openxmlformats-officedocument.extended-properties+xml")])

    parts = [
        ("[Content_Types].xml", ct),
        ("_rels/.rels", rels([
            ("rId1", R + "/officeDocument", "ppt/presentation.xml", False),
            ("rId2", "http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties",
             "docProps/core.xml", False),
            ("rId3", R + "/extended-properties", "docProps/app.xml", False)])),
        ("ppt/presentation.xml", presentation),
        ("ppt/_rels/presentation.xml.rels", rels([
            ("rId1", R + "/slideMaster", "slideMasters/slideMaster1.xml", False),
            ("rId2", R + "/slide", "slides/slide1.xml", False),
            ("rId3", R + "/slide", "slides/slide2.xml", False),
            ("rId4", R + "/slide", "slides/slide3.xml", False)])),
        ("ppt/slides/slide1.xml", slide1),
        ("ppt/slides/slide2.xml", slide2),
        ("ppt/slides/slide3.xml", slide3),
        ("ppt/slides/_rels/slide1.xml.rels", rels([
            ("rId1", R + "/slideLayout", "../slideLayouts/slideLayout1.xml", False)])),
        ("ppt/slides/_rels/slide2.xml.rels", rels([
            ("rId1", R + "/slideLayout", "../slideLayouts/slideLayout1.xml", False),
            ("rId20", R + "/image", "../media/image1.png", False)])),
        ("ppt/slides/_rels/slide3.xml.rels", rels([
            ("rId1", R + "/slideLayout", "../slideLayouts/slideLayout1.xml", False),
            ("rId30", R + "/notesSlide", "../notesSlides/notesSlide3.xml", False)])),
        ("ppt/notesSlides/notesSlide3.xml", notes),
        ("ppt/notesSlides/_rels/notesSlide3.xml.rels", rels([
            ("rId1", R + "/slide", "../slides/slide3.xml", False)])),
        ("ppt/slideLayouts/slideLayout1.xml", layout),
        ("ppt/slideLayouts/_rels/slideLayout1.xml.rels", rels([
            ("rId1", R + "/slideMaster", "../slideMasters/slideMaster1.xml", False)])),
        ("ppt/slideMasters/slideMaster1.xml", master),
        ("ppt/slideMasters/_rels/slideMaster1.xml.rels", rels([
            ("rId1", R + "/slideLayout", "../slideLayouts/slideLayout1.xml", False)])),
        ("ppt/media/image1.png", png_bytes(120, 80)),
        ("docProps/core.xml", core_props("Presentation OpenDocs")),
        ("docProps/app.xml", app_props("OpenDocs sample generator", "<Slides>3</Slides>")),
    ]
    write_zip(os.path.join(out, "exemple.pptx"), parts)


# --------------------------------------------------------------------------- #
# XLSX                                                                         #
# --------------------------------------------------------------------------- #
def col_name(index):
    name = ""
    index += 1
    while index:
        index, rem = divmod(index - 1, 26)
        name = chr(65 + rem) + name
    return name


def make_xlsx(out):
    R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    shared = ["Produit", "Région", "Quantité", "Prix unitaire", "Total", "Date", "Part",
              "Café arabica", "Thé vert", "Chocolat noir", "Nord", "Sud", "Est", "Ouest",
              "Budget 2026", "Poste", "Montant", "Marketing", "R&D", "Total budget"]
    index = {s: i for i, s in enumerate(shared)}

    def cell(ref, value=None, style=None, formula=None, inline=None):
        s = f' s="{style}"' if style is not None else ""
        if formula is not None:
            return f'<c r="{ref}"{s}><f>{formula}</f><v>{value}</v></c>'
        if inline is not None:
            return f'<c r="{ref}"{s} t="inlineStr"><is><t>{esc(inline)}</t></is></c>'
        if isinstance(value, str):
            return f'<c r="{ref}"{s} t="s"><v>{index[value]}</v></c>'
        return f'<c r="{ref}"{s}><v>{value}</v></c>'

    # Feuille 1 : ventes (styles 1=en-tete, 2=entier, 3=monetaire, 4=date, 5=pourcentage)
    rows = []
    header = ["Produit", "Région", "Quantité", "Prix unitaire", "Total", "Date", "Part"]
    rows.append('<row r="1">' + "".join(cell(f"{col_name(c)}1", h, style=1)
                                        for c, h in enumerate(header)) + "</row>")
    data = [("Café arabica", "Nord", 12, 8.5, 102.0, 45658, 0.42),
            ("Thé vert", "Sud", 7, 5.2, 36.4, 45689, 0.15),
            ("Chocolat noir", "Est", 25, 3.75, 93.75, 45720, 0.38),
            ("Café arabica", "Ouest", 4, 8.5, 34.0, 45751, 0.05)]
    for r, row in enumerate(data, start=2):
        cells = [cell(f"A{r}", row[0]), cell(f"B{r}", row[1]),
                 cell(f"C{r}", row[2], style=2), cell(f"D{r}", row[3], style=3),
                 cell(f"E{r}", row[4], style=3, formula=f"C{r}*D{r}"),
                 cell(f"F{r}", row[5], style=4), cell(f"G{r}", row[6], style=5)]
        rows.append(f'<row r="{r}">' + "".join(cells) + "</row>")
    rows.append('<row r="7">' + cell("A7", inline="Total général")
                + cell("E7", 266.15, style=3, formula="SUM(E2:E5)") + "</row>")
    sheet1 = XML_DECL + (
        f'<worksheet {NS_S} {NS_R}>'
        '<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" '
        'activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>'
        "<cols>"
        '<col min="1" max="1" width="18" customWidth="1"/>'
        '<col min="2" max="2" width="12" customWidth="1"/>'
        '<col min="3" max="7" width="14" customWidth="1"/>'
        "</cols>"
        "<sheetData>" + "".join(rows) + "</sheetData>"
        '<mergeCells count="1"><mergeCell ref="A7:D7"/></mergeCells>'
        "</worksheet>")

    # Feuille 2 : budget (styles 6=negatif rouge, 7=scientifique, 1=en-tete)
    rows2 = ['<row r="1">' + cell("A1", "Poste", style=1) + cell("B1", "Montant", style=1) + "</row>",
             '<row r="2">' + cell("A2", "Marketing") + cell("B2", -12500.5, style=6) + "</row>",
             '<row r="3">' + cell("A3", "R&D") + cell("B3", 0.00001234, style=7) + "</row>",
             '<row r="4">' + cell("A4", "Total budget") + cell("B4", 1234567.891, style=3) + "</row>"]
    sheet2 = XML_DECL + (f'<worksheet {NS_S}><sheetData>' + "".join(rows2) + "</sheetData></worksheet>")

    shared_xml = XML_DECL + (
        f'<sst {NS_S} count="{len(shared)}" uniqueCount="{len(shared)}">'
        + "".join(f"<si><t>{esc(s)}</t></si>" for s in shared) + "</sst>")

    styles = XML_DECL + (
        f'<styleSheet {NS_S}>'
        '<numFmts count="4">'
        '<numFmt numFmtId="164" formatCode="#,##0.00\\ &quot;€&quot;"/>'
        '<numFmt numFmtId="165" formatCode="dd/mm/yyyy"/>'
        '<numFmt numFmtId="166" formatCode="#,##0.00;[Red](#,##0.00)"/>'
        '<numFmt numFmtId="167" formatCode="0.00E+00"/>'
        "</numFmts>"
        '<fonts count="2">'
        '<font><sz val="11"/><name val="Calibri"/></font>'
        '<font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>'
        "</fonts>"
        '<fills count="3">'
        '<fill><patternFill patternType="none"/></fill>'
        '<fill><patternFill patternType="gray125"/></fill>'
        '<fill><patternFill patternType="solid"><fgColor rgb="FF2E74B5"/><bgColor indexed="64"/>'
        "</patternFill></fill>"
        "</fills>"
        '<borders count="1"><border/></borders>'
        '<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>'
        '<cellXfs count="8">'
        '<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>'
        '<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>'
        '<xf numFmtId="3" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>'
        '<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>'
        '<xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>'
        '<xf numFmtId="10" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>'
        '<xf numFmtId="166" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>'
        '<xf numFmtId="167" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>'
        "</cellXfs></styleSheet>")

    workbook = XML_DECL + (
        f'<workbook {NS_S} {NS_R}><workbookPr date1904="0"/>'
        '<sheets><sheet name="Ventes" sheetId="1" r:id="rId1"/>'
        '<sheet name="Budget" sheetId="2" r:id="rId2"/></sheets></workbook>')

    parts = [
        ("[Content_Types].xml", content_types(
            [("rels", "application/vnd.openxmlformats-package.relationships+xml"),
             ("xml", "application/xml")],
            [("/xl/workbook.xml",
              "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"),
             ("/xl/worksheets/sheet1.xml",
              "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"),
             ("/xl/worksheets/sheet2.xml",
              "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"),
             ("/xl/styles.xml", "application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"),
             ("/xl/sharedStrings.xml",
              "application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"),
             ("/docProps/core.xml", "application/vnd.openxmlformats-package.core-properties+xml"),
             ("/docProps/app.xml",
              "application/vnd.openxmlformats-officedocument.extended-properties+xml")])),
        ("_rels/.rels", rels([
            ("rId1", R + "/officeDocument", "xl/workbook.xml", False),
            ("rId2", "http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties",
             "docProps/core.xml", False),
            ("rId3", R + "/extended-properties", "docProps/app.xml", False)])),
        ("xl/workbook.xml", workbook),
        ("xl/_rels/workbook.xml.rels", rels([
            ("rId1", R + "/worksheet", "worksheets/sheet1.xml", False),
            ("rId2", R + "/worksheet", "worksheets/sheet2.xml", False),
            ("rId3", R + "/styles", "styles.xml", False),
            ("rId4", R + "/sharedStrings", "sharedStrings.xml", False)])),
        ("xl/worksheets/sheet1.xml", sheet1),
        ("xl/worksheets/sheet2.xml", sheet2),
        ("xl/styles.xml", styles),
        ("xl/sharedStrings.xml", shared_xml),
        ("docProps/core.xml", core_props("Classeur de demonstration")),
        ("docProps/app.xml", app_props("OpenDocs sample generator")),
    ]
    write_zip(os.path.join(out, "exemple.xlsx"), parts)


# --------------------------------------------------------------------------- #
# CSV                                                                          #
# --------------------------------------------------------------------------- #
def make_csv(out):
    fr = (
        "Produit;Région;Quantité;Prix unitaire;Commentaire\n"
        'Café arabica;Nord;12;8,50;"Livraison rapide"\n'
        'Thé vert;Sud;7;5,20;"Lot bio, certifié\n'
        'deux lignes"\n'
        'Chocolat noir;Est;25;3,75;"Prix ""promo"" ce mois"\n'
        "Café arabica;Ouest;4;8,50;\n"
    )
    with open(os.path.join(out, "exemple.csv"), "w", encoding="utf-8-sig", newline="") as f:
        f.write(fr)
    print(f"  {os.path.join(out, 'exemple.csv')}")

    us = ("id,name,score,city\n1,Alice,93.5,Paris\n2,Bob,88,Lyon\n"
          '3,"Doe, John",75.25,Marseille\n4,Chloé,91,Nantes\n')
    with open(os.path.join(out, "exemple_us.csv"), "w", encoding="utf-8", newline="") as f:
        f.write(us)
    print(f"  {os.path.join(out, 'exemple_us.csv')}")

    latin = "Ville;Température;Ressenti\nParis;12,5;11,0\nLyon;15,0;14,2\n"
    with open(os.path.join(out, "exemple_latin1.csv"), "wb") as f:
        f.write(latin.encode("iso-8859-1"))
    print(f"  {os.path.join(out, 'exemple_latin1.csv')}")


def main():
    parser = argparse.ArgumentParser(description="Genere les documents d'exemple OpenDocs")
    parser.add_argument("--out", default="samples", help="dossier de sortie")
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)
    print("Generation des documents d'exemple :")
    make_docx(args.out)
    make_pptx(args.out)
    make_xlsx(args.out)
    make_csv(args.out)
    print("Termine.")


if __name__ == "__main__":
    main()
