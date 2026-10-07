package digital.slovensko.autogram.service;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDObjectReference;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureNode;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;

import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Adds content to the logical structure of an already tagged PDF, so that content added by the service
 * stays accessible (PDF/UA) instead of becoming untagged content.
 */
class TaggedPdf {
    private static final COSName TABS = COSName.getPDFName("Tabs");
    private static final int MAX_DEPTH = 256;

    private final PDStructureTreeRoot root;
    private final Map<COSDictionary, Integer> pageIndexes = new IdentityHashMap<>();

    private TaggedPdf(PDDocument pdf, PDStructureTreeRoot root) {
        this.root = root;

        var index = 0;
        for (var page : pdf.getPages())
            pageIndexes.put(page.getCOSObject(), index++);
    }

    static Optional<TaggedPdf> of(PDDocument pdf) {
        var root = pdf.getDocumentCatalog().getStructureTreeRoot();
        if (root == null)
            return Optional.empty();

        return Optional.of(new TaggedPdf(pdf, root));
    }

    /**
     * Creates a structure element placed in reading order after the content of the given page.
     */
    PDStructureElement addElement(String structureType, PDPage page) {
        var parent = contentParent();
        var element = new PDStructureElement(structureType, parent);
        element.setPage(page);
        insertInReadingOrder(parent, element, pageIndexes.getOrDefault(page.getCOSObject(), Integer.MAX_VALUE));
        return element;
    }

    /**
     * Allocates a new marked-content identifier on the page and links it to the element.
     */
    int addMarkedContent(PDStructureElement element, PDPage page) throws IOException {
        var marks = pageParentTreeArray(page);
        var mcid = Math.max(marks.size(), maxUsedMcid(page) + 1);

        while (marks.size() <= mcid)
            marks.add(COSNull.NULL);

        marks.set(mcid, element);
        element.appendKid(mcid);
        return mcid;
    }

    void addAnnotation(PDStructureElement element, PDAnnotation annotation, PDPage page) {
        var key = allocateParentTreeKey();
        annotation.setStructParent(key);
        insertIntoNumberTree(parentTree(), key, element.getCOSObject());

        var reference = new PDObjectReference();
        reference.setReferencedObject(annotation);
        reference.setPage(page);
        element.appendKid(reference);

        // PDF/UA requires pages with annotations to use structure order for tabbing
        page.getCOSObject().setName(TABS, "S");
    }

    private PDStructureNode contentParent() {
        var kids = root.getKids();
        if (kids.size() == 1 && kids.getFirst() instanceof PDStructureElement documentElement)
            return documentElement;

        return root;
    }

    private void insertInReadingOrder(PDStructureNode parent, PDStructureElement element, int pageIndex) {
        var parentDictionary = parent.getCOSObject();
        var kids = parentDictionary.getDictionaryObject(COSName.K);
        COSArray array;
        if (kids instanceof COSArray existing) {
            array = existing;
        } else {
            array = new COSArray();
            if (kids != null)
                array.add(parentDictionary.getItem(COSName.K));

            parentDictionary.setItem(COSName.K, array);
        }

        for (int index = 0; index < array.size(); index++) {
            var firstPage = firstPageIndex(array.get(index));
            if (firstPage != Integer.MAX_VALUE && firstPage > pageIndex) {
                array.add(index, element.getCOSObject());
                return;
            }
        }

        array.add(element);
    }

    private int firstPageIndex(COSBase node) {
        return firstPageIndex(node, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    }

    private int firstPageIndex(COSBase node, Set<COSBase> visited, int depth) {
        if (node instanceof COSObject object)
            node = object.getObject();

        if (node == null || depth > MAX_DEPTH || !visited.add(node))
            return Integer.MAX_VALUE;

        if (node instanceof COSArray array) {
            var result = Integer.MAX_VALUE;
            for (int index = 0; index < array.size(); index++)
                result = Math.min(result, firstPageIndex(array.get(index), visited, depth + 1));

            return result;
        }

        if (!(node instanceof COSDictionary dictionary))
            return Integer.MAX_VALUE;

        if (dictionary.getDictionaryObject(COSName.PG) instanceof COSDictionary page)
            return pageIndexes.getOrDefault(page, Integer.MAX_VALUE);

        return firstPageIndex(dictionary.getItem(COSName.K), visited, depth + 1);
    }

    private COSArray pageParentTreeArray(PDPage page) {
        var key = page.getCOSObject().getInt(COSName.STRUCT_PARENTS, -1);
        if (key >= 0 && findInNumberTree(parentTree(), key, 0) instanceof COSArray existing)
            return existing;

        if (key < 0) {
            key = allocateParentTreeKey();
            page.setStructParents(key);
        }

        var marks = new COSArray();
        insertIntoNumberTree(parentTree(), key, new COSObject(marks));
        return marks;
    }

    private static int maxUsedMcid(PDPage page) throws IOException {
        var max = -1;
        if (page.hasContents()) {
            for (var token : new PDFStreamParser(page).parse()) {
                if (token instanceof COSDictionary properties)
                    max = Math.max(max, properties.getInt(COSName.MCID, -1));
            }
        }

        var resources = page.getResources();
        if (resources != null) {
            for (var name : resources.getPropertiesNames()) {
                var properties = resources.getProperties(name);
                if (properties != null)
                    max = Math.max(max, properties.getCOSObject().getInt(COSName.MCID, -1));
            }
        }

        return max;
    }

    private COSDictionary parentTree() {
        var tree = root.getCOSObject().getCOSDictionary(COSName.PARENT_TREE);
        if (tree == null) {
            tree = new COSDictionary();
            tree.setItem(COSName.NUMS, new COSArray());
            root.getCOSObject().setItem(COSName.PARENT_TREE, tree);
        }

        return tree;
    }

    private int allocateParentTreeKey() {
        var key = Math.max(root.getParentTreeNextKey(), maxNumberTreeKey(parentTree(), 0) + 1);
        root.setParentTreeNextKey(key + 1);
        return key;
    }

    private static int maxNumberTreeKey(COSDictionary node, int depth) {
        var max = -1;
        if (depth > MAX_DEPTH)
            return max;

        var numbers = node.getCOSArray(COSName.NUMS);
        if (numbers != null) {
            for (int index = 0; index + 1 < numbers.size(); index += 2)
                max = Math.max(max, numbers.getInt(index));
        }

        var kids = node.getCOSArray(COSName.KIDS);
        if (kids != null) {
            for (int index = 0; index < kids.size(); index++) {
                if (kids.getObject(index) instanceof COSDictionary kid)
                    max = Math.max(max, maxNumberTreeKey(kid, depth + 1));
            }
        }

        return max;
    }

    private static COSBase findInNumberTree(COSDictionary node, int key, int depth) {
        if (depth > MAX_DEPTH)
            return null;

        var numbers = node.getCOSArray(COSName.NUMS);
        if (numbers != null) {
            for (int index = 0; index + 1 < numbers.size(); index += 2) {
                if (numbers.getInt(index) == key)
                    return numbers.getObject(index + 1);
            }
        }

        var kids = node.getCOSArray(COSName.KIDS);
        if (kids != null) {
            for (int index = 0; index < kids.size(); index++) {
                if (kids.getObject(index) instanceof COSDictionary kid) {
                    var limits = kid.getCOSArray(COSName.LIMITS);
                    if (limits != null && limits.size() == 2 && (key < limits.getInt(0) || key > limits.getInt(1)))
                        continue;

                    var result = findInNumberTree(kid, key, depth + 1);
                    if (result != null)
                        return result;
                }
            }
        }

        return null;
    }

    private static void insertIntoNumberTree(COSDictionary node, int key, COSBase value) {
        var kids = node.getCOSArray(COSName.KIDS);
        if (kids != null && kids.size() > 0) {
            COSDictionary target = null;
            for (int index = 0; index < kids.size(); index++) {
                if (!(kids.getObject(index) instanceof COSDictionary kid))
                    continue;

                target = kid;
                var limits = kid.getCOSArray(COSName.LIMITS);
                if (limits != null && limits.size() == 2 && key <= limits.getInt(1))
                    break;
            }

            if (target != null) {
                insertIntoNumberTree(target, key, value);
                extendLimits(node, key);
                return;
            }
        }

        var numbers = node.getCOSArray(COSName.NUMS);
        if (numbers == null) {
            numbers = new COSArray();
            node.setItem(COSName.NUMS, numbers);
        }

        var position = numbers.size();
        for (int index = 0; index + 1 < numbers.size(); index += 2) {
            var existingKey = numbers.getInt(index);
            if (existingKey == key) {
                numbers.set(index + 1, value);
                return;
            }

            if (existingKey > key) {
                position = index;
                break;
            }
        }

        numbers.add(position, COSInteger.get(key));
        numbers.add(position + 1, value);
        extendLimits(node, key);
    }

    private static void extendLimits(COSDictionary node, int key) {
        var limits = node.getCOSArray(COSName.LIMITS);
        if (limits == null || limits.size() != 2)
            return;

        limits.set(0, COSInteger.get(Math.min(limits.getInt(0), key)));
        limits.set(1, COSInteger.get(Math.max(limits.getInt(1), key)));
    }
}
