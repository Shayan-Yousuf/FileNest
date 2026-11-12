package filenest;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.*;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.io.*;
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
    private final FileSystemManager fs = new FileSystemManager();
    private JFrame frame;
    private JTree folderTree;
    private DefaultTreeModel treeModel;
    private DefaultTableModel tableModel;
    private JTable fileTable;
    private JLabel statusLabel, storageLabel;
    private JProgressBar storageBar;
    private JTextField searchField;
    private String currentFolderId = "root";
    private final Deque<String> accessHistory = new ArrayDeque<>();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FileNestApp().show());
    }

    private void show() {
        fs.seedDemoData();
        frame = new JFrame("FileNest | Smart File System Simulator");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1050, 680));
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());
        frame.add(buildHeader(), BorderLayout.NORTH);
        frame.add(buildMain(), BorderLayout.CENTER);
        frame.add(buildStatus(), BorderLayout.SOUTH);
        refreshAll();
        frame.setVisible(true);
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 8));
        header.setBorder(new EmptyBorder(16, 18, 16, 18));
        header.setBackground(new Color(25, 38, 58));

        JPanel brand = new JPanel(new GridLayout(2, 1));
        brand.setOpaque(false);
        JLabel title = new JLabel("FileNest");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 25));
        JLabel sub = new JLabel("SMART FILE SYSTEM SIMULATOR  •  JAVA + DSA");
        sub.setForeground(new Color(185, 203, 225));
        sub.setFont(new Font("SansSerif", Font.PLAIN, 10));
        brand.add(title);
        brand.add(sub);
        header.add(brand, BorderLayout.WEST);

        JPanel search = new JPanel(new BorderLayout(6, 0));
        search.setOpaque(false);
        searchField = new JTextField();
        searchField.setPreferredSize(new Dimension(260, 34));
        searchField.setToolTipText("Search file names (prefix search using Trie)");
        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> searchFiles());
        searchField.addActionListener(e -> searchFiles());
        search.add(searchField, BorderLayout.CENTER);
        search.add(searchButton, BorderLayout.EAST);
        header.add(search, BorderLayout.EAST);
        return header;
    }

    private JComponent buildMain() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildSidebar(), buildWorkspace());
        split.setDividerLocation(245);
        split.setBorder(null);
        return split;
    }

    private JComponent buildSidebar() {
        JPanel panel = new JPanel(new BorderLayout(5, 8));
        panel.setBorder(new EmptyBorder(12, 12, 12, 8));
        panel.setBackground(new Color(245, 247, 250));
        JLabel label = new JLabel("FOLDER STRUCTURE");
        label.setFont(new Font("SansSerif", Font.BOLD, 11));
        label.setForeground(new Color(75, 91, 112));
        panel.add(label, BorderLayout.NORTH);

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Home");
        folderTree = new JTree(root);
        folderTree.setRootVisible(true);
        folderTree.setShowsRootHandles(true);
        folderTree.setRowHeight(25);
        folderTree.addTreeSelectionListener(e -> {
            Object node = folderTree.getLastSelectedPathComponent();
            if (node instanceof DefaultMutableTreeNode dm && dm.getUserObject() instanceof FolderRef ref) {
                currentFolderId = ref.id;
                refreshTable();
            }
        });
        treeModel = (DefaultTreeModel) folderTree.getModel();
        panel.add(new JScrollPane(folderTree), BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(2, 1, 5, 5));
        JButton newFolder = new JButton("+ New Folder");
        newFolder.addActionListener(e -> createFolder());
        JButton newFile = new JButton("+ New File");
        newFile.addActionListener(e -> createFile());
        actions.add(newFolder);
        actions.add(newFile);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent buildWorkspace() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(14, 10, 14, 14));
        panel.setBackground(Color.WHITE);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel heading = new JLabel("File Explorer");
        heading.setFont(new Font("SansSerif", Font.BOLD, 20));
        top.add(heading, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        JButton open = new JButton("Access");
        open.addActionListener(e -> accessSelected());
        JButton rename = new JButton("Rename");
        rename.addActionListener(e -> renameSelected());
        JButton move = new JButton("Move");
        move.addActionListener(e -> moveSelected());
        JButton delete = new JButton("Delete");
        delete.addActionListener(e -> deleteSelected());
        JButton undo = new JButton("Undo");
        undo.addActionListener(e -> {
            if (fs.canUndo()) { fs.undo(); refreshAll(); status("Last operation undone."); }
            else status("There is no operation to undo.");
        });
        JButton cleanup = new JButton("Smart Cleanup");
        cleanup.addActionListener(e -> showCleanup());
        JButton history = new JButton("LRU History");
        history.addActionListener(e -> showHistory());
        JButton analytics = new JButton("Analytics");
        analytics.addActionListener(e -> showAnalytics());
        JButton save = new JButton("Save");
        save.addActionListener(e -> saveSnapshot());
        JButton load = new JButton("Load");
        load.addActionListener(e -> loadSnapshot());
        actions.add(open); actions.add(rename); actions.add(move); actions.add(delete); actions.add(undo);
        actions.add(cleanup); actions.add(history); actions.add(analytics); actions.add(save); actions.add(load);
        top.add(actions, BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);

        String[] columns = {"Name", "Type", "Size", "Last Accessed", "Access Count", "ID"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        fileTable = new JTable(tableModel);
        fileTable.setRowHeight(29);
        fileTable.removeColumn(fileTable.getColumnModel().getColumn(5)); // Keep stable IDs hidden from the user.
        fileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        panel.add(new JScrollPane(fileTable), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(8, 6));
        bottom.setOpaque(false);
        storageLabel = new JLabel();
        storageLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        storageBar = new JProgressBar(0, 100);
        storageBar.setStringPainted(true);
        bottom.add(storageLabel, BorderLayout.NORTH);
        bottom.add(storageBar, BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent buildStatus() {
        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(new EmptyBorder(7, 14, 7, 14));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(new Color(235, 240, 246));
        return statusLabel;
    }

    private void refreshAll() {
        refreshTree();
        refreshTable();
        refreshStorage();
    }

    private void refreshTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(new FolderRef("root", "Home"));
        addFolderNodes(root, fs.root);
        treeModel.setRoot(root);
        treeModel.reload();
        expandAll(folderTree);
    }

    private void addFolderNodes(DefaultMutableTreeNode parent, FolderNode folder) {
        for (FsNode child : folder.children.values()) {
            if (child instanceof FolderNode f) {
                DefaultMutableTreeNode swingNode = new DefaultMutableTreeNode(new FolderRef(f.id, f.name));
                parent.add(swingNode);
                addFolderNodes(swingNode, f);
            }
        }
    }

    private void expandAll(JTree tree) {
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        FolderNode folder = fs.folders.get(currentFolderId);
        if (folder == null) { currentFolderId = "root"; folder = fs.root; }
        for (FsNode node : folder.children.values()) {
            if (node instanceof FileNode f) {
                FileRecord r = fs.records.get(f.id);
                tableModel.addRow(new Object[]{f.name, "File", formatBytes(r.size), formatTime(r.lastAccess), r.accessCount, f.id});
            } else if (node instanceof FolderNode f) {
                tableModel.addRow(new Object[]{f.name, "Folder", formatBytes(fs.folderSize(f)), "—", "—", f.id});
            }
        }
    }

    private void refreshStorage() {
        long used = fs.totalSize();
        long capacity = fs.capacityBytes;
        int percent = (int) Math.min(100, Math.round(100.0 * used / capacity));
        storageLabel.setText("Storage used: " + formatBytes(used) + " / " + formatBytes(capacity)
                + "     •     Files: " + fs.records.size() + "     •     Folders: " + (fs.folders.size() - 1));
        storageBar.setValue(percent);
        storageBar.setString(percent + "% used");
    }

    private void createFolder() {
        String name = JOptionPane.showInputDialog(frame, "Folder name:");
        if (name == null || name.isBlank()) return;
        try {
            fs.createFolder(currentFolderId, name.trim());
            refreshAll();
            status("Folder created.");
        } catch (RuntimeException ex) { error(ex.getMessage()); }
    }

    private void createFile() {
        JTextField name = new JTextField();
        JTextField size = new JTextField("1024");
        JTextArea content = new JTextArea(4, 24);
        JPanel form = new JPanel(new GridLayout(3, 2, 8, 8));
        form.add(new JLabel("File name:")); form.add(name);
        form.add(new JLabel("Size in bytes:")); form.add(size);
        form.add(new JLabel("Simulated content:")); form.add(new JScrollPane(content));
        if (JOptionPane.showConfirmDialog(frame, form, "Create File", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try {
            String fileContent = content.getText().isEmpty() ? "Simulated content for " + name.getText().trim() : content.getText();
            fs.createFile(currentFolderId, name.getText().trim(), Long.parseLong(size.getText().trim()), fileContent);
            refreshAll();
            status("File created.");
        } catch (RuntimeException ex) { error(ex.getMessage()); }
    }

    private void accessSelected() {
        FsNode node = selectedNode();
        if (!(node instanceof FileNode file)) { if (node != null) error("Select a file, not a folder."); return; }
        fs.access(file.id);
        accessHistory.addFirst(file.name + "  •  " + formatTime(Instant.now()));
        while (accessHistory.size() > 30) accessHistory.removeLast();
        refreshTable();
        status("Access recorded for " + file.name + " (LRU updated in O(1) average time).");
    }

    private FsNode selectedNode() {
        int row = fileTable.getSelectedRow();
        if (row < 0) { error("Select a file or folder first."); return null; }
        String name = String.valueOf(tableModel.getValueAt(row, 0));
        String id = String.valueOf(tableModel.getValueAt(row, 5));
        try { return fs.findNode(id); }
        catch (RuntimeException ex) { error(ex.getMessage()); return null; }
    }

    private void renameSelected() {
        FsNode node = selectedNode();
        if (node == null) return;
        String newName = JOptionPane.showInputDialog(frame, "New name:", node.name);
        if (newName == null || newName.isBlank() || newName.equals(node.name)) return;
        try {
            fs.renameNode(node.id, newName.trim());
            refreshAll();
            status("Renamed successfully. Search and metadata indexes were updated.");
        } catch (RuntimeException ex) { error(ex.getMessage()); }
    }

    private void moveSelected() {
        FsNode node = selectedNode();
        if (node == null) return;
        List<FolderNode> destinations = new ArrayList<>(fs.folders.values());
        destinations.sort(Comparator.comparing(fs::folderPath));
        String[] labels = destinations.stream().map(f -> fs.folderPath(f) + "  [" + f.id.substring(0, 8) + "]").toArray(String[]::new);
        int choice = JOptionPane.showOptionDialog(frame, "Choose destination folder:", "Move " + node.name,
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, labels, labels[0]);
        if (choice < 0) return;
        try {
            fs.moveNode(node.id, destinations.get(choice).id);
            refreshAll();
            status("Moved successfully to " + fs.folderPath(destinations.get(choice)) + ".");
        } catch (RuntimeException ex) { error(ex.getMessage()); }
    }

    private void deleteSelected() {
        FsNode node = selectedNode();
        if (node == null) return;
        String kind = node instanceof FolderNode ? "folder and all its contents" : "file";
        String detail = node instanceof FolderNode f ? "\nThis contains " + fs.countFiles(f) + " file(s) and " + fs.countFolders(f) + " subfolder(s)." : "";
        if (JOptionPane.showConfirmDialog(frame, "Delete " + kind + " '" + node.name + "'?" + detail,
                "Confirm deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        String parentId = node.parent == null ? "root" : node.parent.id;
        fs.deleteNode(node.id);
        if (!fs.folders.containsKey(currentFolderId)) currentFolderId = parentId;
        refreshAll();
        status("Deleted successfully. Use Undo to restore the complete operation.");
    }

    private void searchFiles() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) { refreshTable(); return; }
        List<FileRecord> results = fs.searchPrefix(query);
        tableModel.setRowCount(0);
        for (FileRecord r : results) {
            tableModel.addRow(new Object[]{r.name, "File", formatBytes(r.size), formatTime(r.lastAccess), r.accessCount, r.id});
        }
        status(results.size() + " result(s) for prefix \"" + query + "\" (Trie search).");
    }

    private void showCleanup() {
        List<FileRecord> candidates = fs.suggestUnused(30, 5);
        if (candidates.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "No unused files found. Access a file or add older demo files.");
            return;
        }
        StringBuilder msg = new StringBuilder("Suggested cleanup candidates (not deleted automatically):\n\n");
        for (FileRecord r : candidates) {
            msg.append("• ").append(r.name).append(" — ").append(formatBytes(r.size))
                    .append(" — last accessed ").append(formatTime(r.lastAccess)).append('\n');
        }
        msg.append("\nReview these files before deleting them.");
        JOptionPane.showMessageDialog(frame, msg.toString(), "Smart Cleanup", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showHistory() {
        List<FileRecord> ordered = fs.lruSnapshot();
        StringBuilder b = new StringBuilder("Least recently used → Most recently used\n\n");
        for (int i=0; i<ordered.size(); i++) b.append(i+1).append(". ").append(ordered.get(i).name)
                .append(" — ").append(formatTime(ordered.get(i).lastAccess)).append('\n');
        b.append("\nRecent access events:\n");
        for (String event : accessHistory) b.append("• ").append(event).append('\n');
        JOptionPane.showMessageDialog(frame, b.toString(), "LRU & Access History", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showAnalytics() {
        StringBuilder b = new StringBuilder("STORAGE ANALYTICS\n\n");
        b.append("Used: ").append(formatBytes(fs.totalSize())).append(" / ").append(formatBytes(fs.capacityBytes)).append('\n');
        b.append("Files: ").append(fs.records.size()).append("\nFolders: ").append(fs.folders.size()-1).append("\n");
        List<FileRecord> largest = new ArrayList<>(fs.records.values());
        largest.sort(Comparator.comparingLong((FileRecord r) -> r.size).reversed());
        b.append("\nLargest files:\n");
        for (int i=0; i<Math.min(5, largest.size()); i++) b.append(i+1).append(". ").append(largest.get(i).name)
                .append(" — ").append(formatBytes(largest.get(i).size)).append('\n');
        b.append("\nFolder usage:\n");
        Map<String, Long> folderSizes = fs.folderSizes();
        for (FolderNode folder : fs.folders.values()) if (folder != fs.root)
            b.append(fs.folderPath(folder)).append(" — ").append(formatBytes(folderSizes.getOrDefault(folder.id, 0L))).append('\n');
        List<FileRecord> duplicates = fs.findDuplicates();
        b.append("\nExact-content duplicate files: ").append(duplicates.size()).append('\n');
        for (FileRecord r : duplicates) b.append("• ").append(r.name).append(" [").append(r.id.substring(0,8)).append("]\n");
        JTextArea area = new JTextArea(b.toString(), 24, 55); area.setEditable(false); area.setCaretPosition(0);
        JOptionPane.showMessageDialog(frame, new JScrollPane(area), "Storage Analytics", JOptionPane.INFORMATION_MESSAGE);
    }

    private void saveSnapshot() {
        JFileChooser chooser = new JFileChooser(); chooser.setSelectedFile(new java.io.File("filenest-save.fns"));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        try { fs.save(chooser.getSelectedFile()); status("Saved FileNest snapshot."); }
        catch (IOException ex) { error("Could not save: " + ex.getMessage()); }
    }

    private void loadSnapshot() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        try { fs.load(chooser.getSelectedFile()); currentFolderId="root"; accessHistory.clear(); refreshAll(); status("Loaded FileNest snapshot."); }
        catch (IOException | RuntimeException ex) { error("Could not load: " + ex.getMessage()); }
    }

    private void status(String text) { statusLabel.setText(text); }
    private void error(String text) { JOptionPane.showMessageDialog(frame, text, "FileNest", JOptionPane.WARNING_MESSAGE); }
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
    private static String formatTime(Instant time) {
        return DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
                .withZone(ZoneId.systemDefault()).format(time);
    }

    private record FolderRef(String id, String name) {
        @Override public String toString() { return name; }
    }

    // ---------------------- DSA CORE ----------------------

    private abstract static class FsNode {
        String id, name;
        FolderNode parent;
        FsNode(String id, String name, FolderNode parent) { this.id=id; this.name=name; this.parent=parent; }
    }

    /** N-ary tree node. HashMap-like child index gives average O(1) child lookup. */
    private static class FolderNode extends FsNode {
        Map<String, FsNode> children = new LinkedHashMap<>();
        FolderNode(String id, String name, FolderNode parent) { super(id, name, parent); }
    }

    private static class FileNode extends FsNode {
        FileNode(String id, String name, FolderNode parent) { super(id, name, parent); }
    }

    private static String hashContent(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 is unavailable.", ex); }
    }

    private static class FileRecord {
        String id, name, folderId, fingerprint, content;
        long size, accessCount, version;
        Instant lastAccess;
        FileRecord(String id, String name, String folderId, long size, String content) {
            this.id=id; this.name=name; this.folderId=folderId; this.size=size; this.content=content;
            this.lastAccess=Instant.now(); this.accessCount=0; this.fingerprint=hashContent(content);
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
        void insert(String word, String id) {
            TrieNode n = root;
            for (char c : word.toLowerCase(Locale.ROOT).toCharArray())
                n = n.children.computeIfAbsent(c, k -> new TrieNode());
            n.ids.add(id);
        }
        void remove(String word, String id) {
            TrieNode n = root;
            for (char c : word.toLowerCase(Locale.ROOT).toCharArray()) {
                n = n.children.get(c);
                if (n == null) return;
            }
            n.ids.remove(id);
        }
        void clear() { root.children.clear(); root.ids.clear(); }
        Set<String> search(String prefix) {
            TrieNode n = root;
            for (char c : prefix.toLowerCase(Locale.ROOT).toCharArray()) {
                n = n.children.get(c);
                if (n == null) return Set.of();
            }
            Set<String> result = new HashSet<>();
            collect(n, result);
            return result;
        }
        private void collect(TrieNode n, Set<String> out) {
            out.addAll(n.ids);
            for (TrieNode child : n.children.values()) collect(child, out);
        }
    }

    private static class FileSystemManager {
        final long capacityBytes = 1024L * 1024 * 1024;
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
        }

        FolderNode createFolder(String parentId, String name) {
            validateName(name);
            FolderNode parent = requireFolder(parentId);
            if (parent.children.containsKey(name)) throw new IllegalArgumentException("A file or folder with that name already exists.");
            FolderNode folder = new FolderNode(UUID.randomUUID().toString(), name, parent);
            parent.children.put(name, folder);
            folders.put(folder.id, folder);
            undoStack.push(() -> { parent.children.remove(name); folders.remove(folder.id); });
            return folder;
        }

        FileRecord createFile(String folderId, String name, long size) {
            return createFile(folderId, name, size, "Simulated file content for " + UUID.randomUUID());
        }

        FileRecord createFile(String folderId, String name, long size, String content) {
            validateName(name);
            if (content == null) content = "";
            if (size < 0) throw new IllegalArgumentException("File size cannot be negative.");
            FolderNode folder = requireFolder(folderId);
            if (folder.children.containsKey(name)) throw new IllegalArgumentException("A file or folder with that name already exists.");
            if (totalSize() + size > capacityBytes) throw new IllegalArgumentException("Not enough simulated storage space.");
            String id = UUID.randomUUID().toString();
            FileNode node = new FileNode(id, name, folder);
            folder.children.put(name, node);
            FileRecord r = new FileRecord(id, name, folderId, size, content);
            registerFile(r, node);
            undoStack.push(() -> { folder.children.remove(name); unregisterFile(r); detachedRecords.remove(r.id); });
            return r;
        }

        void renameNode(String id, String newName) {
            validateName(newName);
            FsNode node = findNode(id);
            if (node == root) throw new IllegalArgumentException("The Home folder cannot be renamed.");
            FolderNode parent = node.parent;
            if (parent.children.containsKey(newName)) throw new IllegalArgumentException("A file or folder with that name already exists here.");
            String oldName = node.name;
            FileRecord r = node instanceof FileNode ? requireRecord(id) : null;
            parent.children.remove(oldName);
            node.name = newName;
            parent.children.put(newName, node);
            if (r != null) { nameIndex.remove(oldName, id); r.name = newName; nameIndex.insert(newName, id); }
            undoStack.push(() -> renameInternal(node, oldName));
        }

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
        }

        private boolean isDescendantOrSame(FolderNode candidate, FolderNode ancestor) {
            FolderNode current = candidate;
            while (current != null) {
                if (current.id.equals(ancestor.id)) return true;
                current = current.parent;
            }
            return false;
        }

        private FsNode findNode(String id) {
            if (root.id.equals(id)) return root;
            FolderNode folder = folders.get(id);
            if (folder != null) return folder;
            FileRecord record = requireRecord(id);
            FolderNode parent = requireFolder(record.folderId);
            FsNode node = parent.children.get(record.name);
            if (node == null) throw new IllegalArgumentException("File system index is inconsistent.");
            return node;
        }

        String folderPath(FolderNode folder) {
            Deque<String> names = new ArrayDeque<>();
            FolderNode current = folder;
            while (current != null) {
                names.push(current.name);
                current = current.parent;
            }
            return String.join("/", names);
        }

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
        }

        void deleteFile(String id) { deleteNode(id); }

        void deleteNode(String id) {
            FsNode node = findNode(id);
            if (node == root) throw new IllegalArgumentException("The Home folder cannot be deleted.");
            FolderNode parent = node.parent;
            String name = node.name;
            List<FsNode> subtree = new ArrayList<>();
            collectNodes(node, subtree);
            List<String> previousLruOrder = new ArrayList<>(lru.keySet());
            parent.children.remove(name);
            for (FsNode item : subtree) {
                if (item instanceof FileNode) {
                    FileRecord record = requireRecord(item.id);
                    record.version++; // invalidate heap entries while the file is detached
                    unregisterFile(record);
                } else folders.remove(item.id);
            }
            undoStack.push(() -> {
                parent.children.put(name, node);
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
        }

        // Records are retained only while a delete is undoable; the undo closure restores their indexes.
        private final Map<String, FileRecord> detachedRecords = new HashMap<>();

        private void collectNodes(FsNode node, List<FsNode> out) {
            out.add(node);
            if (node instanceof FolderNode folder)
                for (FsNode child : folder.children.values()) collectNodes(child, out);
        }

        private void registerFile(FileRecord r, FileNode node) {
            records.put(r.id, r);
            nameIndex.insert(r.name, r.id);
            lru.put(r.id, Boolean.TRUE);
            duplicateIndex.computeIfAbsent(r.fingerprint, k -> new HashSet<>()).add(r.id);
            touchHeap(r);
        }

        private void unregisterFile(FileRecord r) {
            records.remove(r.id);
            nameIndex.remove(r.name, r.id);
            lru.remove(r.id);
            Set<String> ids = duplicateIndex.get(r.fingerprint);
            if (ids != null) { ids.remove(r.id); if (ids.isEmpty()) duplicateIndex.remove(r.fingerprint); }
            detachedRecords.put(r.id, r);
        }

        private void renameInternal(FsNode node, String newName) {
            FolderNode parent = node.parent;
            parent.children.remove(node.name);
            node.name = newName;
            parent.children.put(newName, node);
            if (node instanceof FileNode) {
                FileRecord r = records.get(node.id);
                if (r != null) { nameIndex.remove(r.name, r.id); r.name = newName; nameIndex.insert(newName, r.id); }
            }
        }

        private void moveInternal(FsNode node, FolderNode destination) {
            node.parent.children.remove(node.name);
            destination.children.put(node.name, node);
            node.parent = destination;
            if (node instanceof FileNode) {
                FileRecord r = records.get(node.id);
                if (r != null) r.folderId = destination.id;
            }
        }

        void undo() { if (!undoStack.isEmpty()) undoStack.pop().run(); }
        boolean canUndo() { return !undoStack.isEmpty(); }

        int countFiles(FolderNode folder) {
            int count = 0;
            for (FsNode n : folder.children.values()) count += n instanceof FileNode ? 1 : countFiles((FolderNode)n);
            return count;
        }
        int countFolders(FolderNode folder) {
            int count = 0;
            for (FsNode n : folder.children.values()) if (n instanceof FolderNode f) count += 1 + countFolders(f);
            return count;
        }

        List<FileRecord> findDuplicates() {
            List<FileRecord> result = new ArrayList<>();
            for (Set<String> ids : duplicateIndex.values()) if (ids.size() > 1)
                for (String id : ids) result.add(records.get(id));
            result.sort(Comparator.comparing(r -> r.fingerprint));
            return result;
        }

        List<FileRecord> lruSnapshot() {
            List<FileRecord> result = new ArrayList<>();
            for (String id : lru.keySet()) { FileRecord r = records.get(id); if (r != null) result.add(r); }
            return result;
        }

        void save(File file) throws IOException {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
                out.writeUTF("FILENEST"); out.writeInt(3); out.writeLong(capacityBytes);
                writeFolder(out, root);
                out.writeInt(lru.size());
                for (String id : lru.keySet()) out.writeUTF(id);
            }
        }
        private void writeFolder(DataOutputStream out, FolderNode folder) throws IOException {
            out.writeUTF(folder.id); out.writeUTF(folder.name); out.writeInt(folder.children.size());
            for (FsNode node : folder.children.values()) {
                out.writeBoolean(node instanceof FolderNode);
                if (node instanceof FolderNode child) writeFolder(out, child);
                else {
                    FileRecord r = records.get(node.id);
                    out.writeUTF(r.id); out.writeUTF(r.name); out.writeLong(r.size); writeContent(out, r.content);
                    out.writeLong(r.accessCount); out.writeLong(r.lastAccess.toEpochMilli());
                }
            }
        }
        void load(File file) throws IOException {
            FileSystemManager parsed = new FileSystemManager();
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
                if (!"FILENEST".equals(in.readUTF())) throw new IOException("Not a FileNest save file.");
                int version = in.readInt(); if (version != 3) throw new IOException("Unsupported save version: " + version);
                in.readLong();
                String rootId = in.readUTF(); String rootName = in.readUTF(); int rootCount = in.readInt();
                if (!parsed.root.id.equals(rootId) || !parsed.root.name.equals(rootName)) throw new IOException("Invalid root folder in save file.");
                parsed.readFolderEntries(in, parsed.root, rootCount);
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
            root.children.putAll(parsed.root.children);
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
        }
        private void writeContent(DataOutputStream out, String content) throws IOException {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 64 * 1024 * 1024) throw new IOException("A simulated file's content exceeds the 64 MiB save limit.");
            out.writeInt(bytes.length); out.write(bytes);
        }
        private String readContent(DataInputStream in) throws IOException {
            int length = in.readInt();
            if (length < 0 || length > 64 * 1024 * 1024) throw new IOException("Invalid content length in save file.");
            byte[] bytes = new byte[length]; in.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }
        private void readFolder(DataInputStream in, FolderNode folder) throws IOException {
            int count = in.readInt();
            readFolderEntries(in, folder, count);
        }
        private void readFolderEntries(DataInputStream in, FolderNode folder, int count) throws IOException {
            for (int i=0; i<count; i++) {
                boolean isFolder = in.readBoolean();
                if (isFolder) {
                    String id=in.readUTF(), name=in.readUTF();
                    FolderNode child=new FolderNode(id,name,folder); folder.children.put(name,child); folders.put(id,child);
                    readFolder(in,child);
                } else {
                    String id=in.readUTF(), name=in.readUTF(); long size=in.readLong(); String content=readContent(in);
                    long accesses=in.readLong(), epoch=in.readLong();
                    FileNode node=new FileNode(id,name,folder); folder.children.put(name,node);
                    FileRecord r=new FileRecord(id,name,folder.id,size,content); r.accessCount=accesses; r.lastAccess=Instant.ofEpochMilli(epoch);
                    registerFile(r,node);
                }
            }
        }
        private void reset() {
            root.children.clear(); folders.clear(); folders.put(root.id,root); records.clear();
            nameIndex.clear(); lru.clear(); minHeap.clear(); duplicateIndex.clear(); detachedRecords.clear(); undoStack.clear();
        }

        List<FileRecord> searchPrefix(String prefix) {
            Set<String> ids = nameIndex.search(prefix);
            List<FileRecord> result = new ArrayList<>();
            for (String id : ids) {
                FileRecord r = records.get(id);
                if (r != null) result.add(r);
            }
            result.sort(Comparator.comparing(r -> r.name.toLowerCase(Locale.ROOT)));
            return result;
        }

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
        }

        private void touchHeap(FileRecord r) {
            minHeap.add(new AccessEntry(r.id, r.lastAccess, r.version));
        }

        Map<String, Long> folderSizes() {
            Map<String, Long> sizes = new HashMap<>();
            calculateFolderSizes(root, sizes);
            return sizes;
        }
        private long calculateFolderSizes(FolderNode folder, Map<String, Long> sizes) {
            long total = 0;
            for (FsNode child : folder.children.values()) {
                if (child instanceof FileNode f) total += records.get(f.id).size;
                else if (child instanceof FolderNode f) total += calculateFolderSizes(f, sizes);
            }
            sizes.put(folder.id, total);
            return total;
        }

        long folderSize(FolderNode folder) {
            long total = 0;
            for (FsNode child : folder.children.values()) {
                if (child instanceof FileNode f) total += records.get(f.id).size;
                else if (child instanceof FolderNode f) total += folderSize(f);
            }
            return total;
        }
        long totalSize() { return folderSize(root); }

        private FolderNode requireFolder(String id) {
            FolderNode f = folders.get(id);
            if (f == null) throw new IllegalArgumentException("Folder not found.");
            return f;
        }
        private FileRecord requireRecord(String id) {
            FileRecord r = records.get(id);
            if (r == null) throw new IllegalArgumentException("File not found.");
            return r;
        }
        private static String hashContent(String content) {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(digest);
            } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 is unavailable.", ex); }
        }
        private static void validateName(String name) {
            if (name == null || name.isBlank() || name.contains("/") || name.contains("\\"))
                throw new IllegalArgumentException("Enter a valid name (without / or \\).");
        }
    }
}
