package filenest;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.*;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.List;

/**
 * FileNest - Smart File System Simulator
 * Java 17+, Swing GUI, in-memory simulated file system.
 */
public class FileNestApp {
    // START: main - Application entry point. Opens the separately defined Swing GUI.
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FileNestGUI().show());
    // END: main
    }

    // ---------------------- DSA CORE ----------------------

    abstract static class FsNode {
        String id, name;
        FolderNode parent;
        FsNode(String id, String name, FolderNode parent) { this.id=id; this.name=name; this.parent=parent; }
    }

    /** N-ary tree node. HashMap-like child index gives average O(1) child lookup. */
    static class FolderNode extends FsNode {
        // HashMap provides average O(1) name lookup; TreeMap keeps display/traversal sorted.
        final Map<String, FsNode> children = new HashMap<>();
        final NavigableMap<String, FsNode> sortedChildren = new TreeMap<>(
                Comparator.comparing((String name) -> name.toLowerCase(Locale.ROOT))
                        .thenComparing(Comparator.naturalOrder()));

        FolderNode(String id, String name, FolderNode parent) { super(id, name, parent); }

        // START: addChild - Handles the addChild operation in the FileNest application.
        void addChild(FsNode node) {
            children.put(node.name, node);
            sortedChildren.put(node.name, node);
        // END: addChild
        }

        // START: removeChild - Handles the removeChild operation in the FileNest application.
        FsNode removeChild(String name) {
            sortedChildren.remove(name);
            return children.remove(name);
        // END: removeChild
        }

        // START: clearChildren - Handles the clearChildren operation in the FileNest application.
        void clearChildren() {
            children.clear();
            sortedChildren.clear();
        // END: clearChildren
        }

        // START: addAllChildren - Handles the addAllChildren operation in the FileNest application.
        void addAllChildren(Map<String, FsNode> nodes) {
            for (FsNode node : nodes.values()) addChild(node);
        // END: addAllChildren
        }
    }

    static class FileNode extends FsNode {
        FileNode(String id, String name, FolderNode parent) { super(id, name, parent); }
    }

    // START: hashContent - Calculates a SHA-256 fingerprint for in-memory text content.
    private static String hashContent(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 is unavailable.", ex); }
    // END: hashContent
    }

    static class FileRecord {
        String id, name, folderId, fingerprint, content;
        String physicalPath;
        boolean managedCopy;
        long size, accessCount, version;
        Instant lastAccess;
        // START: FileRecord - Handles the FileRecord operation in the FileNest application.
        FileRecord(String id, String name, String folderId, long size, String content) {
            this.id=id; this.name=name; this.folderId=folderId; this.size=size; this.content=content;
            this.lastAccess=Instant.now(); this.accessCount=0; this.fingerprint=hashContent(content);
        // END: FileRecord
        }
        // START: FileRecord - Handles the FileRecord operation in the FileNest application.
        FileRecord(String id, String name, String folderId, long size, String content, String physicalPath, boolean managedCopy, String fingerprint) {
            this(id, name, folderId, size, content);
            this.physicalPath=physicalPath; this.managedCopy=managedCopy;
            if (fingerprint != null) this.fingerprint=fingerprint;
        // END: FileRecord
        }
    }

    private static class AccessEntry implements Comparable<AccessEntry> {
        String id; Instant time; long version;
        AccessEntry(String id, Instant time, long version) { this.id=id; this.time=time; this.version=version; }
        public int compareTo(AccessEntry other) { return time.compareTo(other.time); }
    }

    /**
     * Trie stores lower-case file names. Each terminal node stores matching file IDs.
     * Search cost: O(L + K), excluding result sorting.
     */
    private static class Trie {
        private static class TrieNode {
            Map<Character, TrieNode> children = new HashMap<>();
            Set<String> ids = new HashSet<>();
        }
        private final TrieNode root = new TrieNode();
        // START: insert - Handles the insert operation in the FileNest application.
        void insert(String word, String id) {
            TrieNode n = root;
            for (char c : word.toLowerCase(Locale.ROOT).toCharArray())
                n = n.children.computeIfAbsent(c, k -> new TrieNode());
            n.ids.add(id);
        // END: insert
        }
        // START: remove - Handles the remove operation in the FileNest application.
        void remove(String word, String id) {
            TrieNode n = root;
            for (char c : word.toLowerCase(Locale.ROOT).toCharArray()) {
                n = n.children.get(c);
                if (n == null) return;
            }
            n.ids.remove(id);
        // END: remove
        }
        void clear() { root.children.clear(); root.ids.clear(); }
        // START: search - Handles the search operation in the FileNest application.
        Set<String> search(String prefix) {
            TrieNode n = root;
            for (char c : prefix.toLowerCase(Locale.ROOT).toCharArray()) {
                n = n.children.get(c);
                if (n == null) return Set.of();
            }
            Set<String> result = new HashSet<>();
            collect(n, result);
            return result;
        // END: search
        }
        // START: collect - Handles the collect operation in the FileNest application.
        private void collect(TrieNode n, Set<String> out) {
            out.addAll(n.ids);
            for (TrieNode child : n.children.values()) collect(child, out);
        // END: collect
        }
    }

    static class FileSystemManager {
        final long capacityBytes = 10L * 1024 * 1024 * 1024;
        final FolderNode root = new FolderNode("root", "Home", null);
        final Map<String, FolderNode> folders = new HashMap<>();
        final Map<String, FileRecord> records = new HashMap<>();
        final Trie nameIndex = new Trie();
        // LinkedHashMap in access-order mode is a hash table + doubly linked list: O(1) average touch.
        final LinkedHashMap<String, Boolean> lru = new LinkedHashMap<>(16, .75f, true);
        final PriorityQueue<AccessEntry> minHeap = new PriorityQueue<>();
        final Map<String, Set<String>> duplicateIndex = new HashMap<>();
        final Deque<Runnable> undoStack = new ArrayDeque<>();
        FileSystemManager() { folders.put(root.id, root); }

        // START: seedDemoData - Handles the seedDemoData operation in the FileNest application.
        void seedDemoData() {
            FolderNode docs = createFolder("root", "Documents");
            FolderNode media = createFolder("root", "Media");
            FolderNode projects = createFolder("root", "Projects");
            createFolder(docs.id, "University");
            createFile(docs.id, "notes.txt", 18000);
            createFile(docs.id, "old_report.pdf", 840000);
            createFile(media.id, "holiday_photo.jpg", 2400000);
            createFile(media.id, "intro_video.mp4", 18000000);
            createFile(projects.id, "FileNest.java", 32000);
            createFile(projects.id, "backup.zip", 6500000);
            // Make selected records appear old so cleanup demo has useful candidates.
            for (FileRecord r : records.values()) {
                if (r.name.startsWith("old_") || r.name.equals("backup.zip")) {
                    r.lastAccess = Instant.now().minus(Duration.ofDays(45));
                    r.version++;
                    minHeap.add(new AccessEntry(r.id, r.lastAccess, r.version));
                }
            }
        // END: seedDemoData
        }

        // START: createFolder - Creates a folder in the current FileNest location.
        FolderNode createFolder(String parentId, String name) {
            validateName(name);
            FolderNode parent = requireFolder(parentId);
            if (parent.children.containsKey(name)) throw new IllegalArgumentException("A file or folder with that name already exists.");
            FolderNode folder = new FolderNode(UUID.randomUUID().toString(), name, parent);
            parent.addChild(folder);
            folders.put(folder.id, folder);
            undoStack.push(() -> { parent.removeChild(name); folders.remove(folder.id); });
            return folder;
        // END: createFolder
        }

        // START: createFile - Creates a file record and registers its indexes and undo action.
        FileRecord createFile(String folderId, String name, long size) {
            return createFile(folderId, name, size, "Simulated file content for " + UUID.randomUUID());
        // END: createFile
        }

        // START: addPhysicalFile - Handles the addPhysicalFile operation in the FileNest application.
        FileRecord addPhysicalFile(String folderId, Path source, boolean copy) throws IOException {
            Path original = source.toAbsolutePath().normalize();
            if (!Files.isRegularFile(original) || !Files.isReadable(original))
                throw new IOException("Select a readable file.");
            FolderNode folder = requireFolder(folderId);
            if (!copy) {
                String originalPath = original.toString();
                for (FileRecord existing : records.values()) {
                    if (originalPath.equals(existing.physicalPath)) return existing;
                }
                String name = original.getFileName().toString();
                validateName(name);
                if (folder.children.containsKey(name))
                    throw new IllegalArgumentException("A file or folder named '" + name + "' already exists in this FileNest folder.");
            }

            long sourceSize = Files.size(original);
            if (sourceSize > capacityBytes - totalSize())
                throw new IllegalArgumentException("The FileNest 10 GB simulated capacity would be exceeded.");

            Path stored = original;
            boolean copied = false;
            try {
                if (copy) {
                    Path storage = Path.of(System.getProperty("user.home"), "FileNest_Storage");
                    Files.createDirectories(storage);
                    stored = reserveCopyDestination(storage, original.getFileName().toString(), folder);
                    try {
                        // The destination was atomically reserved with createFile, so another
                        // import cannot claim the same name between selection and copying.
                        Files.copy(original, stored, StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.COPY_ATTRIBUTES);
                        copied = true;
                    } catch (IOException | RuntimeException ex) {
                        try { Files.deleteIfExists(stored); }
                        catch (IOException cleanup) { ex.addSuppressed(cleanup); }
                        throw ex;
                    }
                }

                String name = stored.getFileName().toString();
                validateName(name);
                if (folder.children.containsKey(name))
                    throw new IllegalArgumentException("A file or folder named '" + name + "' already exists in this FileNest folder.");
                long size = Files.size(stored);
                if (size > capacityBytes - totalSize())
                    throw new IllegalArgumentException("The FileNest 10 GB simulated capacity would be exceeded.");
                String fingerprint = hashPhysicalFile(stored);
                String id = UUID.randomUUID().toString();
                FileNode node = new FileNode(id, name, folder);
                FileRecord record = new FileRecord(id, name, folderId, size, "", stored.toString(), copy, fingerprint);
                try {
                    folder.addChild(node);
                    registerFile(record, node);
                    undoStack.push(() -> { folder.removeChild(name); unregisterFile(record); detachedRecords.remove(record.id); });
                    return record;
                } catch (RuntimeException ex) {
                    folder.removeChild(name);
                    FileRecord partiallyRegistered = records.get(id);
                    if (partiallyRegistered != null) {
                        unregisterFile(partiallyRegistered);
                        detachedRecords.remove(id);
                    }
                    throw ex;
                }
            } catch (IOException | RuntimeException ex) {
                if (copied) {
                    try { Files.deleteIfExists(stored); }
                    catch (IOException cleanup) { ex.addSuppressed(cleanup); }
                }
                throw ex;
            }
        // END: addPhysicalFile
        }

        /**
         * Reserves a collision-free name in storage and in the target virtual folder.
         * Files.createFile performs the reservation atomically.
         */
        // START: reserveCopyDestination - Handles the reserveCopyDestination operation in the FileNest application.
        private Path reserveCopyDestination(Path storage, String fileName, FolderNode folder) throws IOException {
            String base = fileName;
            String extension = "";
            int dot = fileName.lastIndexOf('.');
            if (dot > 0) {
                base = fileName.substring(0, dot);
                extension = fileName.substring(dot);
            }
            for (int suffix = 0; ; suffix++) {
                String candidateName = suffix == 0 ? fileName : base + "_" + suffix + extension;
                validateName(candidateName);
                if (folder.children.containsKey(candidateName)) continue;
                Path candidate = storage.resolve(candidateName);
                try {
                    return Files.createFile(candidate);
                } catch (java.nio.file.FileAlreadyExistsException ignored) {
                    // Try the next suffix without overwriting an existing stored file.
                }
            }
        // END: reserveCopyDestination
        }

        // START: hashPhysicalFile - Calculates a SHA-256 fingerprint by reading a physical file in chunks.
        private String hashPhysicalFile(Path file) throws IOException {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream in = Files.newInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
                }
                return HexFormat.of().formatHex(digest.digest());
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException("SHA-256 is unavailable.", ex);
            }
        // END: hashPhysicalFile
        }

        // START: createFile - Creates a file record and registers its indexes and undo action.
        FileRecord createFile(String folderId, String name, long size, String content) {
            validateName(name);
            if (content == null) content = "";
            if (size < 0) throw new IllegalArgumentException("File size cannot be negative.");
            FolderNode folder = requireFolder(folderId);
            if (folder.children.containsKey(name)) throw new IllegalArgumentException("A file or folder with that name already exists.");
            if (totalSize() + size > capacityBytes) throw new IllegalArgumentException("Not enough simulated storage space.");
            String id = UUID.randomUUID().toString();
            FileNode node = new FileNode(id, name, folder);
            folder.addChild(node);
            FileRecord r = new FileRecord(id, name, folderId, size, content);
            registerFile(r, node);
            undoStack.push(() -> { folder.removeChild(name); unregisterFile(r); detachedRecords.remove(r.id); });
            return r;
        // END: createFile
        }

        // START: renameNode - Renames a file or folder and updates the affected indexes.
        void renameNode(String id, String newName) {
            validateName(newName);
            FsNode node = findNode(id);
            if (node == root) throw new IllegalArgumentException("The Home folder cannot be renamed.");
            FolderNode parent = node.parent;
            if (parent.children.containsKey(newName)) throw new IllegalArgumentException("A file or folder with that name already exists here.");
            String oldName = node.name;
            FileRecord r = node instanceof FileNode ? requireRecord(id) : null;
            parent.removeChild(oldName);
            node.name = newName;
            parent.addChild(node);
            if (r != null) { nameIndex.remove(oldName, id); r.name = newName; nameIndex.insert(newName, id); }
            undoStack.push(() -> renameInternal(node, oldName));
        // END: renameNode
        }

        // START: moveNode - Moves a file or folder while preventing invalid folder cycles.
        void moveNode(String id, String destinationFolderId) {
            FsNode node = findNode(id);
            if (node == root) throw new IllegalArgumentException("The Home folder cannot be moved.");
            FolderNode destination = requireFolder(destinationFolderId);
            if (node.parent == destination) return;
            if (destination.children.containsKey(node.name)) throw new IllegalArgumentException("Destination already contains an item with that name.");
            if (node instanceof FolderNode movingFolder && isDescendantOrSame(destination, movingFolder))
                throw new IllegalArgumentException("A folder cannot be moved into itself or one of its descendants.");

            FolderNode oldParent = node.parent;
            moveInternal(node, destination);
            undoStack.push(() -> moveInternal(node, oldParent));
        // END: moveNode
        }

        // START: isDescendantOrSame - Handles the isDescendantOrSame operation in the FileNest application.
        private boolean isDescendantOrSame(FolderNode candidate, FolderNode ancestor) {
            FolderNode current = candidate;
            while (current != null) {
                if (current.id.equals(ancestor.id)) return true;
                current = current.parent;
            }
            return false;
        // END: isDescendantOrSame
        }

        // START: findNode - Handles the findNode operation in the FileNest application.
        private FsNode findNode(String id) {
            if (root.id.equals(id)) return root;
            FolderNode folder = folders.get(id);
            if (folder != null) return folder;
            FileRecord record = requireRecord(id);
            FolderNode parent = requireFolder(record.folderId);
            FsNode node = parent.children.get(record.name);
            if (node == null) throw new IllegalArgumentException("File system index is inconsistent.");
            return node;
        // END: findNode
        }

        // START: folderPath - Handles the folderPath operation in the FileNest application.
        String folderPath(FolderNode folder) {
            Deque<String> names = new ArrayDeque<>();
            FolderNode current = folder;
            while (current != null) {
                names.push(current.name);
                current = current.parent;
            }
            return String.join("/", names);
        // END: folderPath
        }

        // START: access - Updates last-access time, access count, LRU order, and heap state for a file.
        void access(String id) {
            FileRecord r = requireRecord(id);
            Instant oldTime = r.lastAccess;
            long oldCount = r.accessCount;
            List<String> oldOrder = new ArrayList<>(lru.keySet());
            r.lastAccess = Instant.now();
            r.accessCount++;
            r.version++;
            lru.get(id); // access-order LinkedHashMap moves the entry to most-recently-used position.
            touchHeap(r);
            undoStack.push(() -> {
                r.lastAccess = oldTime;
                r.accessCount = oldCount;
                r.version++;
                lru.clear();
                for (String fileId : oldOrder) if (records.containsKey(fileId)) lru.put(fileId, Boolean.TRUE);
                touchHeap(r);
            });
        // END: access
        }

        void deleteFile(String id) { deleteNode(id); }

        // START: deleteNode - Removes a file or folder subtree from the virtual tree and stores an undo action.
        void deleteNode(String id) {
            FsNode node = findNode(id);
            if (node == root) throw new IllegalArgumentException("The Home folder cannot be deleted.");
            FolderNode parent = node.parent;
            String name = node.name;
            List<FsNode> subtree = new ArrayList<>();
            collectNodes(node, subtree);
            List<String> previousLruOrder = new ArrayList<>(lru.keySet());
            parent.removeChild(name);
            for (FsNode item : subtree) {
                if (item instanceof FileNode) {
                    FileRecord record = requireRecord(item.id);
                    record.version++; // invalidate heap entries while the file is detached
                    unregisterFile(record);
                } else folders.remove(item.id);
            }
            undoStack.push(() -> {
                parent.addChild(node);
                for (FsNode item : subtree) {
                    if (item instanceof FolderNode folder) folders.put(folder.id, folder);
                    else {
                        FileRecord r = detachedRecords.get(item.id);
                        if (r != null) { r.version++; registerFile(r, (FileNode) item); }
                    }
                }
                lru.clear();
                for (String fileId : previousLruOrder) if (records.containsKey(fileId)) lru.put(fileId, Boolean.TRUE);
                for (String fileId : records.keySet()) if (!lru.containsKey(fileId)) lru.put(fileId, Boolean.TRUE);
                detachedRecords.keySet().removeAll(subtree.stream().filter(n -> n instanceof FileNode).map(n -> n.id).toList());
            });
        // END: deleteNode
        }

        // Records are retained only while a delete is undoable; the undo closure restores their indexes.
        private final Map<String, FileRecord> detachedRecords = new HashMap<>();

        // START: collectNodes - Handles the collectNodes operation in the FileNest application.
        private void collectNodes(FsNode node, List<FsNode> out) {
            out.add(node);
            if (node instanceof FolderNode folder)
                for (FsNode child : folder.sortedChildren.values()) collectNodes(child, out);
        // END: collectNodes
        }

        // START: registerFile - Adds a file to the primary records map, Trie, LRU map, duplicate index, and heap.
        private void registerFile(FileRecord r, FileNode node) {
            records.put(r.id, r);
            nameIndex.insert(r.name, r.id);
            lru.put(r.id, Boolean.TRUE);
            duplicateIndex.computeIfAbsent(r.fingerprint, k -> new HashSet<>()).add(r.id);
            touchHeap(r);
        // END: registerFile
        }

        // START: unregisterFile - Removes a file from active indexes and retains its record for a possible undo.
        private void unregisterFile(FileRecord r) {
            records.remove(r.id);
            nameIndex.remove(r.name, r.id);
            lru.remove(r.id);
            Set<String> ids = duplicateIndex.get(r.fingerprint);
            if (ids != null) { ids.remove(r.id); if (ids.isEmpty()) duplicateIndex.remove(r.fingerprint); }
            detachedRecords.put(r.id, r);
        // END: unregisterFile
        }

        // START: renameInternal - Performs the internal tree and index updates required by a rename or undo.
        private void renameInternal(FsNode node, String newName) {
            FolderNode parent = node.parent;
            parent.removeChild(node.name);
            node.name = newName;
            parent.addChild(node);
            if (node instanceof FileNode) {
                FileRecord r = records.get(node.id);
                if (r != null) { nameIndex.remove(r.name, r.id); r.name = newName; nameIndex.insert(newName, r.id); }
            }
        // END: renameInternal
        }

        // START: moveInternal - Performs the internal tree and metadata updates required by a move or undo.
        private void moveInternal(FsNode node, FolderNode destination) {
            node.parent.removeChild(node.name);
            destination.addChild(node);
            node.parent = destination;
            if (node instanceof FileNode) {
                FileRecord r = records.get(node.id);
                if (r != null) r.folderId = destination.id;
            }
        // END: moveInternal
        }

        void undo() { if (!undoStack.isEmpty()) undoStack.pop().run(); }
        boolean canUndo() { return !undoStack.isEmpty(); }

        // START: countFiles - Handles the countFiles operation in the FileNest application.
        int countFiles(FolderNode folder) {
            int count = 0;
            for (FsNode n : folder.sortedChildren.values()) count += n instanceof FileNode ? 1 : countFiles((FolderNode)n);
            return count;
        // END: countFiles
        }
        // START: countFolders - Handles the countFolders operation in the FileNest application.
        int countFolders(FolderNode folder) {
            int count = 0;
            for (FsNode n : folder.sortedChildren.values()) if (n instanceof FolderNode f) count += 1 + countFolders(f);
            return count;
        // END: countFolders
        }

        // START: findDuplicates - Groups exact-content matches using SHA-256 fingerprints.
        List<FileRecord> findDuplicates() {
            List<FileRecord> result = new ArrayList<>();
            for (Set<String> ids : duplicateIndex.values()) if (ids.size() > 1)
                for (String id : ids) result.add(records.get(id));
            result.sort(Comparator.comparing(r -> r.fingerprint));
            return result;
        // END: findDuplicates
        }

        // START: lruSnapshot - Returns file records in least-recently-used order.
        List<FileRecord> lruSnapshot() {
            List<FileRecord> result = new ArrayList<>();
            for (String id : lru.keySet()) { FileRecord r = records.get(id); if (r != null) result.add(r); }
            return result;
        // END: lruSnapshot
        }

        // START: save - Serializes the folder tree, file metadata, and LRU order into a snapshot.
        void save(File file) throws IOException {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
                out.writeUTF("FILENEST"); out.writeInt(4); out.writeLong(capacityBytes);
                writeFolder(out, root);
                out.writeInt(lru.size());
                for (String id : lru.keySet()) out.writeUTF(id);
            }
        // END: save
        }
        // START: writeFolder - Writes a folder and its descendants recursively to the snapshot stream.
        private void writeFolder(DataOutputStream out, FolderNode folder) throws IOException {
            out.writeUTF(folder.id); out.writeUTF(folder.name); out.writeInt(folder.children.size());
            for (FsNode node : folder.sortedChildren.values()) {
                out.writeBoolean(node instanceof FolderNode);
                if (node instanceof FolderNode child) writeFolder(out, child);
                else {
                    FileRecord r = records.get(node.id);
                    out.writeUTF(r.id); out.writeUTF(r.name); out.writeLong(r.size); writeContent(out, r.content);
                    out.writeLong(r.accessCount); out.writeLong(r.lastAccess.toEpochMilli());
                    out.writeUTF(r.physicalPath == null ? "" : r.physicalPath);
                    out.writeBoolean(r.managedCopy);
                }
            }
        // END: writeFolder
        }
        // START: load - Validates and restores a saved snapshot, then rebuilds indexes.
        void load(File file) throws IOException {
            FileSystemManager parsed = new FileSystemManager();
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
                if (!"FILENEST".equals(in.readUTF())) throw new IOException("Not a FileNest save file.");
                int version = in.readInt(); if (version != 3 && version != 4) throw new IOException("Unsupported save version: " + version);
                in.readLong();
                String rootId = in.readUTF(); String rootName = in.readUTF(); int rootCount = in.readInt();
                if (!parsed.root.id.equals(rootId) || !parsed.root.name.equals(rootName)) throw new IOException("Invalid root folder in save file.");
                parsed.readFolderEntries(in, parsed.root, rootCount, version);
                int lruCount = in.readInt();
                if (lruCount != parsed.records.size()) throw new IOException("Invalid LRU index in save file.");
                List<String> savedOrder = new ArrayList<>(); Set<String> seen = new HashSet<>();
                for (int i=0; i<lruCount; i++) {
                    String id = in.readUTF();
                    if (!parsed.records.containsKey(id) || !seen.add(id)) throw new IOException("Invalid file ID in saved LRU index.");
                    savedOrder.add(id);
                }
                parsed.lru.clear();
                for (String id : savedOrder) parsed.lru.put(id, Boolean.TRUE);
            } catch (EOFException ex) { throw new IOException("Save file is incomplete or corrupted.", ex); }
            // Adopt only after the complete snapshot has parsed, so a bad file cannot wipe the current session.
            reset();
            root.addAllChildren(parsed.root.children);
            for (FsNode child : root.children.values()) child.parent = root;
            folders.putAll(parsed.folders);
            folders.put(root.id, root);
            records.putAll(parsed.records);
            for (String id : parsed.lru.keySet()) lru.put(id, Boolean.TRUE);
            for (FileRecord r : records.values()) {
                nameIndex.insert(r.name, r.id);
                duplicateIndex.computeIfAbsent(r.fingerprint, k -> new HashSet<>()).add(r.id);
                touchHeap(r);
            }
            undoStack.clear(); detachedRecords.clear();
        // END: load
        }
        // START: writeContent - Handles the writeContent operation in the FileNest application.
        private void writeContent(DataOutputStream out, String content) throws IOException {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 64 * 1024 * 1024) throw new IOException("A simulated file's content exceeds the 64 MiB save limit.");
            out.writeInt(bytes.length); out.write(bytes);
        // END: writeContent
        }
        // START: readContent - Handles the readContent operation in the FileNest application.
        private String readContent(DataInputStream in) throws IOException {
            int length = in.readInt();
            if (length < 0 || length > 64 * 1024 * 1024) throw new IOException("Invalid content length in save file.");
            byte[] bytes = new byte[length]; in.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        // END: readContent
        }
        // START: readFolder - Reads a folder's children from the snapshot stream.
        private void readFolder(DataInputStream in, FolderNode folder, int version) throws IOException {
            int count = in.readInt();
            readFolderEntries(in, folder, count, version);
        // END: readFolder
        }
        // START: readFolderEntries - Reconstructs folder and file entries and registers their indexes while loading.
        private void readFolderEntries(DataInputStream in, FolderNode folder, int count, int version) throws IOException {
            for (int i=0; i<count; i++) {
                boolean isFolder = in.readBoolean();
                if (isFolder) {
                    String id=in.readUTF(), name=in.readUTF();
                    FolderNode child=new FolderNode(id,name,folder); folder.addChild(child); folders.put(id,child);
                    readFolder(in,child,version);
                } else {
                    String id=in.readUTF(), name=in.readUTF(); long size=in.readLong(); String content=readContent(in);
                    long accesses=in.readLong(), epoch=in.readLong();
                    String physicalPath = version >= 4 ? in.readUTF() : "";
                    boolean managedCopy = version >= 4 && in.readBoolean();
                    FileNode node=new FileNode(id,name,folder); folder.addChild(node);
                    FileRecord r;
                    if (!physicalPath.isEmpty()) {
                        String fingerprint = Files.exists(Path.of(physicalPath)) ? hashPhysicalFile(Path.of(physicalPath)) : hashContent(content);
                        r = new FileRecord(id,name,folder.id,size,content,physicalPath,managedCopy,fingerprint);
                    } else r=new FileRecord(id,name,folder.id,size,content);
                    r.accessCount=accesses; r.lastAccess=Instant.ofEpochMilli(epoch);
                    registerFile(r,node);
                }
            }
        // END: readFolderEntries
        }
        // START: reset - Clears the current in-memory indexes and undo state before loading a snapshot.
        private void reset() {
            root.clearChildren(); folders.clear(); folders.put(root.id,root); records.clear();
            nameIndex.clear(); lru.clear(); minHeap.clear(); duplicateIndex.clear(); detachedRecords.clear(); undoStack.clear();
        // END: reset
        }

        // START: searchPrefix - Uses the Trie index to find file records whose names match a prefix.
        List<FileRecord> searchPrefix(String prefix) {
            Set<String> ids = nameIndex.search(prefix);
            List<FileRecord> result = new ArrayList<>();
            for (String id : ids) {
                FileRecord r = records.get(id);
                if (r != null) result.add(r);
            }
            result.sort(Comparator.comparing(r -> r.name.toLowerCase(Locale.ROOT)));
            return result;
        // END: searchPrefix
        }

        // START: suggestUnused - Uses the timestamp min-heap to suggest files older than the requested age.
        List<FileRecord> suggestUnused(int minAgeDays, int limit) {
            Instant cutoff = Instant.now().minus(Duration.ofDays(minAgeDays));
            List<FileRecord> result = new ArrayList<>();
            List<AccessEntry> deferred = new ArrayList<>();
            while (!minHeap.isEmpty() && result.size() < limit) {
                AccessEntry entry = minHeap.poll();
                FileRecord r = records.get(entry.id);
                if (r == null || r.version != entry.version) continue; // discard stale heap entry
                if (entry.time.isAfter(cutoff)) { deferred.add(entry); break; }
                result.add(r);
            }
            minHeap.addAll(deferred);
            return result;
        // END: suggestUnused
        }

        // START: touchHeap - Adds a versioned access-time entry to the priority queue.
        private void touchHeap(FileRecord r) {
            minHeap.add(new AccessEntry(r.id, r.lastAccess, r.version));
        // END: touchHeap
        }

        // START: folderSizes - Handles the folderSizes operation in the FileNest application.
        Map<String, Long> folderSizes() {
            Map<String, Long> sizes = new HashMap<>();
            calculateFolderSizes(root, sizes);
            return sizes;
        // END: folderSizes
        }
        // START: calculateFolderSizes - Calculates folder sizes recursively and stores each result by folder ID.
        private long calculateFolderSizes(FolderNode folder, Map<String, Long> sizes) {
            long total = 0;
            for (FsNode child : folder.sortedChildren.values()) {
                if (child instanceof FileNode f) total += records.get(f.id).size;
                else if (child instanceof FolderNode f) total += calculateFolderSizes(f, sizes);
            }
            sizes.put(folder.id, total);
            return total;
        // END: calculateFolderSizes
        }

        // START: folderSize - Calculates the total logical size of a folder and all descendants.
        long folderSize(FolderNode folder) {
            long total = 0;
            for (FsNode child : folder.sortedChildren.values()) {
                if (child instanceof FileNode f) total += records.get(f.id).size;
                else if (child instanceof FolderNode f) total += folderSize(f);
            }
            return total;
        // END: folderSize
        }
        long totalSize() { return folderSize(root); }

        // START: requireFolder - Handles the requireFolder operation in the FileNest application.
        private FolderNode requireFolder(String id) {
            FolderNode f = folders.get(id);
            if (f == null) throw new IllegalArgumentException("Folder not found.");
            return f;
        // END: requireFolder
        }
        // START: requireRecord - Handles the requireRecord operation in the FileNest application.
        private FileRecord requireRecord(String id) {
            FileRecord r = records.get(id);
            if (r == null) throw new IllegalArgumentException("File not found.");
            return r;
        // END: requireRecord
        }
        // START: hashContent - Calculates a SHA-256 fingerprint for in-memory text content.
        private static String hashContent(String content) {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(digest);
            } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 is unavailable.", ex); }
        // END: hashContent
        }
        // START: validateName - Validates a file or folder name before it is added or renamed.
        private static void validateName(String name) {
            if (name == null || name.isBlank() || name.contains("/") || name.contains("\\"))
                throw new IllegalArgumentException("Enter a valid name (without / or \\).");
        // END: validateName
        }
    }
}
