package digital.slovensko.autogram.service;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkInfo;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.StandardStructureTypes;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

final class TaggedPdfFixtures {
    private TaggedPdfFixtures() {
    }

    /**
     * Creates a tagged PDF with structure Document → H1 (page 1) → P (page 2).
     */
    static byte[] createTaggedPdf() throws IOException {
        try (var pdf = new PDDocument()) {
            var font = PDType0Font.load(pdf, TaggedPdfFixtures.class.getResourceAsStream("/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf"));
            var catalog = pdf.getDocumentCatalog();
            catalog.setLanguage("sk");

            var markInfo = new PDMarkInfo();
            markInfo.setMarked(true);
            catalog.setMarkInfo(markInfo);

            var root = new PDStructureTreeRoot();
            catalog.setStructureTreeRoot(root);

            var document = new PDStructureElement(StandardStructureTypes.DOCUMENT, root);
            root.appendKid(document);

            var parentTree = new COSArray();
            String[][] content = {{StandardStructureTypes.H1, "Zmluva"}, {StandardStructureTypes.P, "Druhá strana"}};
            for (int index = 0; index < content.length; index++) {
                var page = new PDPage();
                pdf.addPage(page);
                page.setStructParents(index);

                var element = new PDStructureElement(content[index][0], document);
                element.setPage(page);
                element.appendKid(0);
                document.appendKid(element);

                try (var stream = new PDPageContentStream(pdf, page)) {
                    stream.beginMarkedContent(COSName.getPDFName(content[index][0]), 0);
                    stream.beginText();
                    stream.setFont(font, 12);
                    stream.newLineAtOffset(72, 700);
                    stream.showText(content[index][1]);
                    stream.endText();
                    stream.endMarkedContent();
                }

                var marks = new COSArray();
                marks.add(element);
                parentTree.add(COSInteger.get(index));
                parentTree.add(new COSObject(marks));
            }

            var parentTreeDictionary = new COSDictionary();
            parentTreeDictionary.setItem(COSName.NUMS, parentTree);
            root.getCOSObject().setItem(COSName.PARENT_TREE, parentTreeDictionary);
            root.setParentTreeNextKey(content.length);

            var output = new ByteArrayOutputStream();
            pdf.save(output);
            return output.toByteArray();
        }
    }

    static COSArray parentTreeNumbers(PDDocument pdf) {
        return pdf.getDocumentCatalog().getStructureTreeRoot().getCOSObject()
                .getCOSDictionary(COSName.PARENT_TREE)
                .getCOSArray(COSName.NUMS);
    }

    static Object parentTreeValue(PDDocument pdf, int key) {
        var numbers = parentTreeNumbers(pdf);
        for (int index = 0; index + 1 < numbers.size(); index += 2) {
            if (numbers.getInt(index) == key)
                return numbers.getObject(index + 1);
        }

        return null;
    }
}
