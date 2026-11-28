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
    private final FileSystemManager fs = new FileSystemManager();
    private JFrame frame;
    private JTree folderTree;
    private DefaultTreeModel treeModel;
    private DefaultTableModel tableModel;
    private JTable fileTable;
    private JLabel statusLabel, storageLabel;
    private JProgressBar storageBar;
    private JTextField searchField;
    private JLabel pathLabel, previewIcon, previewName, previewMeta, previewHint;
    private JButton previewOpenButton;
    private JButton themeToggleButton;
    private boolean darkTheme = true;
    private Path pcBrowsePath;
    private final Map<String, File> pcBrowseEntries = new LinkedHashMap<>();
    private final Map<String, ImageIcon> thumbnailCache = new HashMap<>();
    private String currentFolderId = "root";
    private final Deque<String> accessHistory = new ArrayDeque<>();

    // START: main - Application entry point. Schedules the Swing GUI to start on the Event Dispatch Thread.
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FileNestApp().show());
    // END: main
    }

    // START: show - Builds and displays the complete FileNest desktop window. GUI START/END.
    private void show() {
        fs.seedDemoData();
        frame = new JFrame("FileNest | Simulated File Manager");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1180, 720));
        frame.getContentPane().setBackground(new Color(29, 29, 29));
        frame.setLayout(new BorderLayout());
        frame.add(buildHeader(), BorderLayout.NORTH);
        frame.add(buildMain(), BorderLayout.CENTER);
        frame.add(buildStatus(), BorderLayout.SOUTH);
        applyTheme();
        refreshAll();
        frame.setLocationByPlatform(true);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setVisible(true);
        frame.addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override public void windowGainedFocus(java.awt.event.WindowEvent e) {
                if (pcBrowsePath != null && Files.isDirectory(pcBrowsePath)) {
                    SwingUtilities.invokeLater(() -> {
                        refreshTable();
                        fileTable.revalidate();
                        fileTable.repaint();
                    });
                }
            }
        });
    // END: show
    }

    // START: buildHeader - Builds the top header, navigation path, prefix-search controls, and theme switch.
    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(39, 54, 77)), new EmptyBorder(15, 22, 15, 22)));
        header.setBackground(new Color(15, 23, 42));

        JPanel brandBlock = new JPanel();
        brandBlock.setOpaque(false);
        brandBlock.setLayout(new BoxLayout(brandBlock, BoxLayout.Y_AXIS));
        JLabel brand = new JLabel("◆  FileNest");
        brand.setForeground(new Color(235, 243, 255));
        brand.setFont(new Font("SansSerif", Font.BOLD, 24));
        JLabel tagline = new JLabel("SMART FILE WORKSPACE");
        tagline.setForeground(new Color(111, 151, 205));
        tagline.setFont(new Font("SansSerif", Font.BOLD, 9));
        brandBlock.add(brand); brandBlock.add(Box.createVerticalStrut(2)); brandBlock.add(tagline);
        header.add(brandBlock, BorderLayout.WEST);

        JPanel address = new JPanel(new BorderLayout(8, 0));
        address.setBackground(new Color(24, 36, 56));
        address.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(47, 67, 96)), new EmptyBorder(10, 15, 10, 15)));
        pathLabel = new JLabel("Home  /  All files");
        pathLabel.setForeground(new Color(194, 211, 235));
        pathLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        address.add(pathLabel, BorderLayout.CENTER);
        header.add(address, BorderLayout.CENTER);

        JPanel search = new JPanel(new BorderLayout(6, 0));
        search.setOpaque(false);
        searchField = new JTextField();
        searchField.setPreferredSize(new Dimension(210, 34));
        searchField.setToolTipText("Search file names using Trie prefix search");
        styleInput(searchField);
        JButton searchButton = createButton("Search", true);
        searchButton.addActionListener(e -> searchFiles());
        searchField.addActionListener(e -> searchFiles());
        search.add(searchField, BorderLayout.CENTER);
        search.add(searchButton, BorderLayout.EAST);
        themeToggleButton = createButton("☀ Light mode", false);
        themeToggleButton.setToolTipText("Switch between dark and light appearance");
        themeToggleButton.addActionListener(e -> {
            darkTheme = !darkTheme;
            applyTheme();
        });
        JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        headerActions.setOpaque(false);
        headerActions.add(search);
        headerActions.add(themeToggleButton);
        header.add(headerActions, BorderLayout.EAST);
        return header;
    // END: buildHeader
    }

    // START: buildMain - Combines the sidebar and main workspace into the central GUI layout.
    private JComponent buildMain() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildSidebar(), buildWorkspace());
        split.setBorder(null);
        split.setContinuousLayout(true);
        split.setOneTouchExpandable(true);
        split.setDividerSize(5);
        split.setBackground(new Color(29, 29, 29));
        split.setResizeWeight(0.20);
        split.setDividerLocation(218);
        split.setMinimumSize(new Dimension(700, 400));
        return split;
    // END: buildMain
    }

    // START: buildSidebar - Builds the sidebar, folder tree, pinned PC locations, drive shortcuts, and create buttons.
    private JComponent buildSidebar() {
        JPanel panel = new JPanel(new BorderLayout(8, 10));
        panel.setBorder(new EmptyBorder(22, 16, 18, 14));
        panel.setBackground(new Color(17, 27, 45));
        panel.setMinimumSize(new Dimension(215, 300));
        panel.setPreferredSize(new Dimension(248, 600));

        JPanel upper = new JPanel();
        upper.setOpaque(false);
        upper.setLayout(new BoxLayout(upper, BoxLayout.Y_AXIS));
        JLabel tabs = sidebarHeading("WORKSPACE");
        upper.add(tabs);
        upper.add(sidebarButton("⌂   Home", () -> { pcBrowsePath = null; currentFolderId = "root"; refreshAll(); }));
        upper.add(sidebarButton("＋   Import files", this::importFiles));
        upper.add(Box.createVerticalStrut(15));
        upper.add(sidebarHeading("PINNED"));
        upper.add(sidebarButton("▣   Downloads", () -> browsePcFolder(Path.of(System.getProperty("user.home"), "Downloads"))));
        upper.add(sidebarButton("▣   Documents", () -> browsePcFolder(Path.of(System.getProperty("user.home"), "Documents"))));
        upper.add(sidebarButton("▧   Pictures", () -> browsePcFolder(resolveWindowsKnownFolder("MyPictures", "Pictures"))));
        upper.add(sidebarButton("▣   Desktop", () -> browsePcFolder(resolveWindowsKnownFolder("Desktop", "Desktop"))));
        upper.add(Box.createVerticalStrut(15));
        upper.add(sidebarHeading("DRIVES"));
        for (File drive : File.listRoots()) {
            String driveName = drive.getPath();
            upper.add(sidebarButton("▰   " + driveName, () -> browseDrive(drive)));
        }
        upper.add(Box.createVerticalStrut(15));
        upper.add(sidebarHeading("FILENEST FOLDERS"));

        DefaultMutableTreeNode root = new DefaultMutableTreeNode(new FolderRef("root", "⌂  Home"));
        folderTree = new JTree(root);
        folderTree.setRootVisible(true);
        folderTree.setShowsRootHandles(true);
        folderTree.setRowHeight(27);
        folderTree.setBackground(new Color(37, 37, 37));
        folderTree.setForeground(new Color(135, 190, 250));
        folderTree.setFont(new Font("SansSerif", Font.PLAIN, 12));
        folderTree.setBorder(new EmptyBorder(5, 2, 5, 2));
        // START: DefaultTreeCellRenderer - Handles the DefaultTreeCellRenderer operation in the FileNest application.
        folderTree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                    boolean expanded, boolean leaf, int row, boolean hasFocus) {
                JLabel label = (JLabel) super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
                label.setOpaque(true);
                label.setBackground(selected ? (darkTheme ? new Color(35, 65, 96) : new Color(205, 228, 250))
                        : (darkTheme ? new Color(37, 37, 37) : new Color(250, 252, 255)));
                label.setForeground(selected ? new Color(35, 125, 215)
                        : (darkTheme ? new Color(205, 220, 238) : new Color(45, 65, 88)));
                label.setBorder(new EmptyBorder(3, 4, 3, 4));
                return label;
            }
        // END: DefaultTreeCellRenderer
        });
        folderTree.addTreeSelectionListener(e -> {
            Object node = folderTree.getLastSelectedPathComponent();
            if (node instanceof DefaultMutableTreeNode dm && dm.getUserObject() instanceof FolderRef ref) {
                pcBrowsePath = null;
                currentFolderId = ref.id;
                refreshTable();
            }
        });
        treeModel = (DefaultTreeModel) folderTree.getModel();

        JPanel treeArea = new JPanel(new BorderLayout());
        treeArea.setOpaque(false);
        JScrollPane folderScroll = new JScrollPane(folderTree);
        styleScrollPane(folderScroll);
        folderScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        treeArea.add(folderScroll, BorderLayout.CENTER);
        upper.add(treeArea);
        panel.add(upper, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(2, 1, 7, 7));
        actions.setOpaque(false);
        JButton newFolder = createButton("+ New Folder", false);
        newFolder.addActionListener(e -> createFolder());
        JButton newFile = createButton("+ New File", false);
        newFile.addActionListener(e -> createFile());
        actions.add(newFolder);
        actions.add(newFile);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    // END: buildSidebar
    }

    // START: sidebarHeading - Handles the sidebarHeading operation in the FileNest application.
    private JLabel sidebarHeading(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(105, 145, 199));
        label.setFont(new Font("SansSerif", Font.BOLD, 10));
        label.setBorder(new EmptyBorder(12, 10, 7, 4));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    // END: sidebarHeading
    }

    // START: sidebarButton - Handles the sidebarButton operation in the FileNest application.
    private JButton sidebarButton(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setForeground(new Color(196, 211, 232));
        button.setBackground(new Color(17, 27, 45));
        button.setBorder(new EmptyBorder(10, 12, 10, 8));
        button.setFocusPainted(false);
        button.setFont(new Font("SansSerif", Font.PLAIN, 13));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.addActionListener(e -> action.run());
        return button;
    // END: sidebarButton
    }

    // START: resolveWindowsKnownFolder - Resolves a Windows known folder and falls back to common user-folder paths.
    private Path resolveWindowsKnownFolder(String specialFolder, String fallbackName) {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            try {
                Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command",
                        "[Environment]::GetFolderPath('" + specialFolder + "')")
                        .redirectErrorStream(true).start();
                if (process.waitFor(4, java.util.concurrent.TimeUnit.SECONDS)) {
                    String resolved = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                    if (!resolved.isBlank()) {
                        Path path = Path.of(resolved);
                        if (Files.isDirectory(path)) return path;
                    }
                } else process.destroyForcibly();
            } catch (Exception ignored) { }
        }
        List<Path> candidates = new ArrayList<>();
        String home = System.getProperty("user.home");
        candidates.add(Path.of(home, fallbackName));
        String oneDrive = System.getenv("OneDrive");
        if (oneDrive != null && !oneDrive.isBlank()) candidates.add(Path.of(oneDrive, fallbackName));
        String oneDriveConsumer = System.getenv("OneDriveConsumer");
        if (oneDriveConsumer != null && !oneDriveConsumer.isBlank()) candidates.add(Path.of(oneDriveConsumer, fallbackName));
        candidates.add(Path.of(home, "OneDrive", fallbackName));
        candidates.add(Path.of(home, "OneDrive - Personal", fallbackName));
        for (Path candidate : candidates) if (Files.isDirectory(candidate)) return candidate;
        return candidates.get(0);
    // END: resolveWindowsKnownFolder
    }

    // START: isImageFile - Checks whether a filename has a supported image extension.
    private boolean isImageFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.matches(".*\\.(png|jpe?g|gif|bmp|wbmp|tif|tiff)$");
    // END: isImageFile
    }

    // START: getThumbnail - Loads, scales, and caches an image thumbnail for the preview/table.
    private ImageIcon getThumbnail(File file, int width, int height) {
        if (file == null || !file.isFile() || !isImageFile(file)) return null;
        String cacheKey = file.getAbsolutePath() + "|" + file.lastModified() + "|" + width + "x" + height;
        if (thumbnailCache.containsKey(cacheKey)) return thumbnailCache.get(cacheKey);
        try {
            Image image = ImageIO.read(file);
            if (image == null) return null;
            double scale = Math.min((double) width / image.getWidth(null), (double) height / image.getHeight(null));
            int scaledWidth = Math.max(1, (int) Math.round(image.getWidth(null) * scale));
            int scaledHeight = Math.max(1, (int) Math.round(image.getHeight(null) * scale));
            Image scaled = image.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
            ImageIcon icon = new ImageIcon(scaled);
            thumbnailCache.put(cacheKey, icon);
            return icon;
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    // END: getThumbnail
    }

    // START: browseDrive - Opens a selected PC drive in direct-browse mode.
    private void browseDrive(File drive) {
        browsePcFolder(drive.toPath());
    // END: browseDrive
    }

    // START: browsePcFolder - Displays a real PC folder without copying its contents into FileNest.
    private void browsePcFolder(Path directory) {
        if (!Files.isDirectory(directory) || !Files.isReadable(directory)) {
            error("This folder is unavailable or you do not have permission to access it.");
            return;
        }
        pcBrowsePath = directory.toAbsolutePath().normalize();
        currentFolderId = "root";
        if (fileTable != null) fileTable.clearSelection();
        pcBrowseEntries.clear();
        if (pathLabel != null) pathLabel.setText(pcBrowsePath.toString());
        refreshTable();
        if (fileTable != null) {
            fileTable.revalidate();
            fileTable.repaint();
        }
        updatePreviewPanel();
        status("Browsing PC folder directly: " + pcBrowsePath);
    // END: browsePcFolder
    }

    // START: choosePcFolder - Handles the choosePcFolder operation in the FileNest application.
    private void choosePcFolder(String folderName) {
        browsePcFolder(Path.of(System.getProperty("user.home"), folderName));
    // END: choosePcFolder
    }

    // START: navigateBack - Navigates to the parent PC folder or parent FileNest folder.
    private void navigateBack() {
        if (pcBrowsePath != null) {
            Path parent = pcBrowsePath.getParent();
            if (parent == null || parent.equals(pcBrowsePath)) {
                pcBrowsePath = null;
                currentFolderId = "root";
                pcBrowseEntries.clear();
                refreshAll();
                updatePreviewPanel();
                status("Returned to FileNest Home.");
            } else {
                browsePcFolder(parent);
            }
            return;
        }

        FolderNode current = fs.folders.get(currentFolderId);
        if (current == null || current.parent == null) {
            status("You are already at Home.");
            return;
        }
        currentFolderId = current.parent.id;
        if (folderTree != null) {
            TreePath parentPath = findTreePath(currentFolderId);
            if (parentPath != null) folderTree.setSelectionPath(parentPath);
        }
        refreshTable();
        updatePreviewPanel();
        status("Returned to parent folder.");
    // END: navigateBack
    }

    // START: findTreePath - Handles the findTreePath operation in the FileNest application.
    private TreePath findTreePath(String folderId) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        Enumeration<?> nodes = root.depthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
            Object value = node.getUserObject();
            if (value instanceof FolderRef ref && ref.id.equals(folderId)) {
                return new TreePath(node.getPath());
            }
        }
        return null;
    // END: findTreePath
    }

    // START: buildWorkspace - Builds the main file workspace, toolbar, file table, preview, and action controls.
    private JComponent buildWorkspace() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(20, 22, 18, 22));
        panel.setBackground(new Color(11, 18, 32));

        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.setOpaque(false);
        JPanel headingRow = new JPanel(new BorderLayout());
        headingRow.setOpaque(false);
        JLabel heading = new JLabel("Your files");
        heading.setForeground(new Color(235, 243, 255));
        heading.setFont(new Font("SansSerif", Font.BOLD, 25));
        headingRow.add(heading, BorderLayout.WEST);
        JLabel hint = new JLabel("Browse, organize and understand your files");
        hint.setForeground(new Color(125, 151, 184));
        hint.setFont(new Font("SansSerif", Font.PLAIN, 12));
        headingRow.add(hint, BorderLayout.EAST);
        top.add(headingRow, BorderLayout.NORTH);

        // Keep every workspace action on one compact horizontal toolbar row.
        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        actionBar.setOpaque(false);
        JButton backButton = createButton("← Back", false);
        backButton.setToolTipText("Go to the parent folder");
        backButton.addActionListener(e -> navigateBack());
        actionBar.add(backButton);
        JButton[] buttons = {
            createButton("＋ Import", true), createButton("Rename", false),
            createButton("Move", false), createButton("Delete", false),
            createButton("Undo", false), createButton("Cleanup", false),
            createButton("History", false), createButton("✦ Analyze", false),
            createButton("Scan", true), createButton("Save", false), createButton("Load", false)
        };
        buttons[0].addActionListener(e -> importFiles());
        buttons[1].addActionListener(e -> renameSelected());
        buttons[2].addActionListener(e -> moveSelected());
        buttons[3].addActionListener(e -> deleteSelected());
        buttons[4].addActionListener(e -> { if (fs.canUndo()) { fs.undo(); refreshAll(); status("Last operation undone."); } else status("Nothing to undo yet."); });
        buttons[5].addActionListener(e -> showCleanup());
        buttons[6].addActionListener(e -> showHistory());
        buttons[7].addActionListener(e -> showAnalytics());
        buttons[8].addActionListener(e -> scanSelection());
        buttons[9].addActionListener(e -> saveSnapshot());
        buttons[10].addActionListener(e -> loadSnapshot());
        for (JButton button : buttons) {
            button.setFont(new Font("SansSerif", Font.BOLD, 11));
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(button == buttons[0] || button == buttons[8]
                            ? new Color(72, 139, 255) : new Color(48, 67, 96)),
                    new EmptyBorder(6, 8, 6, 8)));
            actionBar.add(button);
        }
        backButton.setFont(new Font("SansSerif", Font.BOLD, 11));
        backButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(48, 67, 96)),
                new EmptyBorder(6, 8, 6, 8)));
        top.add(actionBar, BorderLayout.CENTER);
        panel.add(top, BorderLayout.NORTH);

        String[] columns = {"Name", "Type", "Size", "Last Accessed", "Access Count", "ID"};
        // START: DefaultTableModel - Handles the DefaultTableModel operation in the FileNest application.
        tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        // END: DefaultTableModel
        };
        fileTable = new JTable(tableModel);
        fileTable.setRowHeight(52);
        fileTable.setFillsViewportHeight(true);
        fileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Keyboard shortcuts must work whenever the file list has focus.
        InputMap fileInputMap = fileTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap fileActionMap = fileTable.getActionMap();
        fileInputMap.put(KeyStroke.getKeyStroke("DELETE"), "filenest-delete-selected");
        // START: AbstractAction - Handles the AbstractAction operation in the FileNest application.
        fileActionMap.put("filenest-delete-selected", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (fileTable.getSelectedRow() >= 0) deleteSelected();
                else status("Select a file or folder first, then press Delete.");
            }
        // END: AbstractAction
        });
        fileInputMap.put(KeyStroke.getKeyStroke("BACK_SPACE"), "filenest-go-back");
        // START: AbstractAction - Handles the AbstractAction operation in the FileNest application.
        fileActionMap.put("filenest-go-back", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                navigateBack();
            }
        // END: AbstractAction
        });
        fileTable.setShowGrid(false);
        fileTable.setIntercellSpacing(new Dimension(0, 0));
        fileTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        fileTable.setBackground(new Color(21, 33, 52));
        fileTable.setForeground(new Color(220, 231, 247));
        fileTable.setSelectionBackground(new Color(34, 67, 111));
        fileTable.setSelectionForeground(Color.WHITE);
        fileTable.setFont(new Font("SansSerif", Font.PLAIN, 13));
        fileTable.setRowSelectionAllowed(true);
        fileTable.setGridColor(new Color(48, 48, 48));
        fileTable.setTableHeader(null);
        // START: DefaultTableCellRenderer - Handles the DefaultTableCellRenderer operation in the FileNest application.
        fileTable.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                    boolean focused, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, selected, focused, row, column);
                label.setBorder(new EmptyBorder(2, 8, 2, 4));
                label.setIcon(null);
                label.setIconTextGap(10);
                int modelRow = table.convertRowIndexToModel(row);
                String key = String.valueOf(table.getModel().getValueAt(modelRow, 5));
                File imageFile = pcBrowseEntries.get(key);
                if (imageFile != null && imageFile.isFile()) {
                    ImageIcon thumbnail = getThumbnail(imageFile, 38, 38);
                    if (thumbnail != null) label.setIcon(thumbnail);
                }
                return label;
            }
        // END: DefaultTableCellRenderer
        });
        fileTable.setShowHorizontalLines(false);
        fileTable.setShowVerticalLines(false);
        fileTable.getColumnModel().getColumn(0).setPreferredWidth(360);
        fileTable.removeColumn(fileTable.getColumnModel().getColumn(1));
        fileTable.removeColumn(fileTable.getColumnModel().getColumn(1));
        fileTable.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                    boolean focus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, selected, focus, row, column);
                String name = String.valueOf(value);
                Object idValue = tableModel.getValueAt(table.convertRowIndexToModel(row), 5);
                FsNode node = null;
                String rowId = String.valueOf(idValue);
                try { node = fs.findNode(rowId); } catch (RuntimeException ignored) { }
                File pcEntry = pcBrowseEntries.get(rowId);
                boolean folder = pcEntry != null && pcEntry.isDirectory() || node instanceof FolderNode;
                label.setText((folder ? "   ▰   " : "   ▧   ") + name);
                label.setFont(new Font("SansSerif", Font.PLAIN, 13));
                label.setForeground(selected ? new Color(35, 125, 215)
                        : (folder ? new Color(45, 135, 220) : (darkTheme ? new Color(215, 225, 238) : new Color(45, 55, 70))));
                label.setBackground(selected ? (darkTheme ? new Color(38, 65, 92) : new Color(205, 228, 250))
                        : (darkTheme ? new Color(43, 43, 43) : Color.WHITE));
                label.setBorder(new EmptyBorder(0, 8, 0, 8));
                label.setOpaque(true);
                return label;
            }
        });
        fileTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updatePreviewPanel();
        });
        fileTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    if (pcBrowsePath != null) {
                        int row = fileTable.getSelectedRow();
                        if (row >= 0) {
                            String key = String.valueOf(tableModel.getValueAt(fileTable.convertRowIndexToModel(row), 5));
                            File entry = pcBrowseEntries.get(key);
                            if (entry != null && entry.isDirectory()) browsePcFolder(entry.toPath());
                            else if (entry != null) openPcBrowserFile(entry);
                        }
                    } else {
                        FsNode selected = selectedNode();
                        if (selected instanceof FolderNode folder) {
                            currentFolderId = folder.id;
                            refreshAll();
                        } else if (selected instanceof FileNode) previewSelected();
                    }
                }
            }
        });
        fileTable.setToolTipText("Double-click to open a file.");
        JScrollPane tableScroll = new JScrollPane(fileTable);
        styleScrollPane(tableScroll);
        JPanel listCard = darkCard(new BorderLayout());
        listCard.add(tableScroll, BorderLayout.CENTER);

        JPanel previewCard = darkCard(new BorderLayout(8, 10));
        previewCard.setBorder(new EmptyBorder(22, 20, 22, 20));
        JLabel previewHeading = new JLabel("PREVIEW");
        previewHeading.setForeground(new Color(111, 151, 205));
        previewHeading.setFont(new Font("SansSerif", Font.BOLD, 10));
        previewCard.add(previewHeading, BorderLayout.NORTH);
        JPanel previewBody = new JPanel();
        previewBody.setOpaque(false);
        previewBody.setLayout(new BoxLayout(previewBody, BoxLayout.Y_AXIS));
        previewIcon = new JLabel("▧", SwingConstants.CENTER);
        previewIcon.setForeground(new Color(90, 165, 255));
        previewIcon.setFont(new Font("SansSerif", Font.PLAIN, 76));
        previewIcon.setPreferredSize(new Dimension(320, 230));
        previewIcon.setAlignmentX(Component.CENTER_ALIGNMENT);
        previewName = new JLabel("Choose a file");
        previewName.setForeground(new Color(232, 241, 255));
        previewName.setFont(new Font("SansSerif", Font.BOLD, 15));
        previewName.setAlignmentX(Component.CENTER_ALIGNMENT);
        previewMeta = new JLabel("File information will appear here");
        previewMeta.setForeground(new Color(155, 155, 155));
        previewMeta.setFont(new Font("SansSerif", Font.PLAIN, 11));
        previewMeta.setAlignmentX(Component.CENTER_ALIGNMENT);
        previewHint = new JLabel("Select an item from the list");
        previewHint.setForeground(new Color(135, 135, 135));
        previewHint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        previewHint.setAlignmentX(Component.CENTER_ALIGNMENT);
        previewOpenButton = createButton("Open file", false);
        previewOpenButton.setVisible(false);
        previewOpenButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        previewOpenButton.addActionListener(e -> previewSelected());
        previewBody.add(Box.createVerticalGlue());
        previewBody.add(previewIcon);
        previewBody.add(Box.createVerticalStrut(18));
        previewBody.add(previewName);
        previewBody.add(Box.createVerticalStrut(7));
        previewBody.add(previewMeta);
        previewBody.add(Box.createVerticalStrut(12));
        previewBody.add(previewHint);
        previewBody.add(Box.createVerticalStrut(18));
        previewBody.add(previewOpenButton);
        previewBody.add(Box.createVerticalGlue());
        previewCard.add(previewBody, BorderLayout.CENTER);

        JSplitPane contentSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listCard, previewCard);
        contentSplit.setBorder(null);
        contentSplit.setDividerSize(5);
        contentSplit.setBackground(new Color(29, 29, 29));
        contentSplit.setResizeWeight(0.52);
        contentSplit.setDividerLocation(0.52);
        panel.add(contentSplit, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(8, 6));
        bottom.setOpaque(false);
        storageLabel = new JLabel();
        storageLabel.setForeground(new Color(155, 180, 211));
        storageLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        storageBar = new JProgressBar(0, 100);
        storageBar.setStringPainted(true);
        storageBar.setForeground(new Color(65, 145, 235));
        storageBar.setBorderPainted(false);
        storageBar.setBackground(new Color(55, 55, 55));
        bottom.add(storageLabel, BorderLayout.NORTH);
        bottom.add(storageBar, BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    // END: buildWorkspace
    }

    // START: createButton - Handles the createButton operation in the FileNest application.
    private JButton createButton(String text, boolean primary) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? new Color(72, 139, 255) : new Color(48, 67, 96)),
                new EmptyBorder(9, 14, 9, 14)));
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setBackground(primary ? new Color(55, 119, 230) : new Color(25, 41, 65));
        button.setForeground(primary ? Color.WHITE : new Color(185, 211, 246));
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    // END: createButton
    }

    // START: styleInput - Handles the styleInput operation in the FileNest application.
    private void styleInput(JTextField field) {
        field.setBackground(new Color(24, 36, 56));
        field.setForeground(new Color(232, 241, 255));
        field.setCaretColor(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(65, 65, 65)),
                new EmptyBorder(7, 9, 7, 9)));
    // END: styleInput
    }

    // START: darkCard - Handles the darkCard operation in the FileNest application.
    private JPanel darkCard(LayoutManager layout) {
        JPanel card = new JPanel(layout);
        card.setBackground(new Color(19, 31, 50));
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(37, 55, 82)), BorderFactory.createEmptyBorder(1, 1, 1, 1)));
        return card;
    // END: darkCard
    }

    // START: applyTheme - Applies the selected dark or light theme to the GUI components.
    private void applyTheme() {
        if (frame == null) return;
        Color background = darkTheme ? new Color(11, 18, 32) : new Color(239, 245, 253);
        Color panel = darkTheme ? new Color(17, 27, 45) : new Color(255, 255, 255);
        Color surface = darkTheme ? new Color(19, 31, 50) : new Color(248, 251, 255);
        Color foreground = darkTheme ? new Color(225, 236, 250) : new Color(35, 48, 65);
        Color muted = darkTheme ? new Color(137, 160, 190) : new Color(100, 115, 132);
        Color border = darkTheme ? new Color(42, 61, 89) : new Color(211, 224, 241);
        applyThemeTo(frame.getContentPane(), background, panel, surface, foreground, muted, border);
        frame.getContentPane().setBackground(background);
        if (themeToggleButton != null) {
            themeToggleButton.setText(darkTheme ? "☀ Light mode" : "☾ Dark mode");
            themeToggleButton.setBackground(new Color(55, 119, 230));
            themeToggleButton.setForeground(Color.WHITE);
        }
        if (fileTable != null) {
            fileTable.setBackground(surface);
            fileTable.setForeground(foreground);
            fileTable.setSelectionBackground(darkTheme ? new Color(38, 65, 92) : new Color(205, 228, 250));
            fileTable.setSelectionForeground(darkTheme ? new Color(135, 195, 255) : new Color(30, 95, 165));
        }
        if (folderTree != null) {
            folderTree.setBackground(surface);
            folderTree.setForeground(foreground);
        }
        if (storageBar != null) {
            storageBar.setBackground(darkTheme ? new Color(55, 55, 55) : new Color(220, 229, 239));
            storageBar.setForeground(new Color(64, 143, 255));
        }
        frame.repaint();
    // END: applyTheme
    }

    private void applyThemeTo(Component component, Color background, Color panel, Color surface,
            Color foreground, Color muted, Color border) {
        if (component instanceof JSplitPane split) split.setBackground(background);
        if (component instanceof JPanel p) {
            Color old = p.getBackground();
            if (old != null && old.getRed() < 80 && old.getGreen() < 80 && old.getBlue() < 80) p.setBackground(panel);
            else p.setBackground(surface);
        } else if (component instanceof JLabel label) {
            String text = label.getText() == null ? "" : label.getText();
            label.setForeground(text.equals("PREVIEW") || text.matches("[A-Z ]{3,}") ? muted : foreground);
        } else if (component instanceof JButton button) {
            if (button != themeToggleButton) {
                button.setBackground(darkTheme ? new Color(25, 41, 65) : new Color(231, 240, 252));
                button.setForeground(darkTheme ? new Color(185, 211, 246) : new Color(35, 105, 175));
                button.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(border), new EmptyBorder(7, 11, 7, 11)));
            }
        } else if (component instanceof JTextField field) {
            field.setBackground(surface); field.setForeground(foreground);
            field.setCaretColor(foreground);
        } else if (component instanceof JProgressBar bar) {
            bar.setBackground(darkTheme ? new Color(55, 55, 55) : new Color(220, 229, 239));
            bar.setForeground(new Color(45, 135, 220));
        } else if (component instanceof JScrollPane scroll) {
            scroll.setBackground(surface);
            scroll.getViewport().setBackground(surface);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents())
                applyThemeTo(child, background, panel, surface, foreground, muted, border);
        }
    }

    // START: styleScrollPane - Handles the styleScrollPane operation in the FileNest application.
    private void styleScrollPane(JScrollPane scroll) {
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setBackground(new Color(43, 43, 43));
        scroll.getViewport().setBackground(new Color(43, 43, 43));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().setBackground(new Color(43, 43, 43));
        scroll.getVerticalScrollBar().setForeground(new Color(65, 145, 235));
        scroll.getHorizontalScrollBar().setBackground(new Color(43, 43, 43));
    // END: styleScrollPane
    }

    // START: JTableHeaderStyle - Handles the JTableHeaderStyle operation in the FileNest application.
    private void JTableHeaderStyle(JTable table) {
        table.getTableHeader().setBackground(new Color(37, 37, 37));
        table.getTableHeader().setForeground(new Color(165, 165, 165));
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 10));
        table.getTableHeader().setPreferredSize(new Dimension(0, 34));
        table.getTableHeader().setReorderingAllowed(false);
    // END: JTableHeaderStyle
    }

    // START: buildStatus - Handles the buildStatus operation in the FileNest application.
    private JComponent buildStatus() {
        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(new EmptyBorder(7, 14, 7, 14));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(new Color(13, 22, 38));
        statusLabel.setForeground(new Color(145, 174, 211));
        return statusLabel;
    // END: buildStatus
    }

    // START: refreshAll - Refreshes the folder tree, file table, storage meter, and related GUI state.
    private void refreshAll() {
        refreshTree();
        refreshTable();
        refreshStorage();
    // END: refreshAll
    }

    // START: refreshTree - Rebuilds the visible folder tree from the in-memory N-ary tree.
    private void refreshTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(new FolderRef("root", "Home"));
        addFolderNodes(root, fs.root);
        treeModel.setRoot(root);
        treeModel.reload();
        expandAll(folderTree);
    // END: refreshTree
    }

    // START: addFolderNodes - Recursively adds child folders to the Swing tree model.
    private void addFolderNodes(DefaultMutableTreeNode parent, FolderNode folder) {
        for (FsNode child : folder.sortedChildren.values()) {
            if (child instanceof FolderNode f) {
                DefaultMutableTreeNode swingNode = new DefaultMutableTreeNode(new FolderRef(f.id, f.name));
                parent.add(swingNode);
                addFolderNodes(swingNode, f);
            }
        }
    // END: addFolderNodes
    }

    // START: expandAll - Handles the expandAll operation in the FileNest application.
    private void expandAll(JTree tree) {
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
    // END: expandAll
    }

    // START: refreshTable - Loads the current folder or directly browsed PC directory into the file table.
    private void refreshTable() {
        // In PC-browse mode, read the directory before clearing the current rows.
        // File.listFiles() can temporarily return null (for example, during a
        // Windows focus/application hand-off). Keep the previous listing visible
        // instead of making the whole file area appear empty.
        if (pcBrowsePath != null) {
            File directory = pcBrowsePath.toFile();
            File[] entries = directory.listFiles();
            if (entries == null) {
                if (pathLabel != null) pathLabel.setText(pcBrowsePath.toString());
                status("Could not refresh this PC folder; showing the previous listing. Check folder access and try again.");
                return;
            }
            Arrays.sort(entries, Comparator.comparing(File::isFile).thenComparing(f -> f.getName().toLowerCase(Locale.ROOT)));
            tableModel.setRowCount(0);
            pcBrowseEntries.clear();
            if (pathLabel != null) pathLabel.setText(pcBrowsePath.toString());
            for (File entry : entries) {
                String key = "PCFS:" + entry.getAbsolutePath();
                pcBrowseEntries.put(key, entry);
                String type = entry.isDirectory() ? "Folder" : "PC File";
                String size = entry.isFile() ? formatBytes(entry.length()) : "—";
                tableModel.addRow(new Object[]{entry.getName(), type, size, "—", "—", key});
            }
            if (previewName != null) updatePreviewPanel();
            return;
        }

        tableModel.setRowCount(0);
        thumbnailCache.keySet().removeIf(key -> {
            int split = key.lastIndexOf('|');
            if (split < 0) return true;
            String path = key.substring(0, split);
            return !Files.exists(Path.of(path));
        });
        FolderNode folder = fs.folders.get(currentFolderId);
        if (folder == null) { currentFolderId = "root"; folder = fs.root; }
        if (pathLabel != null) pathLabel.setText("Home  /  " + fs.folderPath(folder).replace("/", "  /  "));
        for (FsNode node : folder.sortedChildren.values()) {
            if (node instanceof FileNode f) {
                FileRecord r = fs.records.get(f.id);
                tableModel.addRow(new Object[]{f.name, r.physicalPath == null ? "File" : (r.managedCopy ? "PC Copy" : "PC Link"), formatBytes(r.size), formatTime(r.lastAccess), r.accessCount, f.id});
            } else if (node instanceof FolderNode f) {
                tableModel.addRow(new Object[]{f.name, "Folder", formatBytes(fs.folderSize(f)), "—", "—", f.id});
            }
        }
        if (previewName != null) updatePreviewPanel();
    // END: refreshTable
    }

    // START: updatePreviewPanel - Updates the preview area for the currently selected file.
    private void updatePreviewPanel() {
        int row = fileTable == null ? -1 : fileTable.getSelectedRow();
        if (row >= 0 && pcBrowsePath != null) {
            String key = String.valueOf(tableModel.getValueAt(fileTable.convertRowIndexToModel(row), 5));
            File entry = pcBrowseEntries.get(key);
            if (entry == null) return;
            previewIcon.setIcon(null);
            previewIcon.setText(entry.isDirectory() ? "📁" : "▤");
            if (entry.isFile()) {
                ImageIcon thumbnail = getThumbnail(entry, 300, 210);
                if (thumbnail != null) {
                    previewIcon.setText("");
                    previewIcon.setIcon(thumbnail);
                }
            }
            previewName.setText(entry.getName());
            previewMeta.setText(entry.isFile() ? formatBytes(entry.length()) + "  •  Original PC file" : "Folder  •  Direct PC access");
            previewHint.setText(entry.isDirectory() ? "Double-click to browse this folder" : "Opens with your PC's default application");
            previewOpenButton.setVisible(entry.isFile());
            return;
        }
        if (row < 0) {
            previewIcon.setIcon(null);
            previewIcon.setText("▧");
            previewName.setText("Choose a file");
            previewMeta.setText("File information will appear here");
            previewHint.setText("Select an item from the list");
            previewOpenButton.setVisible(false);
            return;
        }
        String id = String.valueOf(tableModel.getValueAt(fileTable.convertRowIndexToModel(row), 5));
        if (id.startsWith("PCFS:")) {
            File entry = pcBrowseEntries.get(id);
            if (entry != null && entry.isFile()) openPcBrowserFile(entry);
            return;
        }
        FsNode node;
        try { node = fs.findNode(id); } catch (RuntimeException ex) { return; }
        if (node instanceof FolderNode folder) {
            previewIcon.setIcon(null); previewIcon.setText("📁");
            previewName.setText(folder.name);
            previewMeta.setText(fs.countFiles(folder) + " files  •  " + formatBytes(fs.folderSize(folder)));
            previewHint.setText("Folder");
            previewOpenButton.setVisible(false);
            return;
        }
        FileRecord record = fs.records.get(id);
        if (record == null) return;
        previewName.setText(record.name);
        previewMeta.setText(formatBytes(record.size) + "  •  " + (record.physicalPath == null ? "FileNest file" : (record.managedCopy ? "PC copy" : "Direct PC access")));
        String lower = record.name.toLowerCase(Locale.ROOT);
        previewIcon.setIcon(null);
        if (record.physicalPath != null && (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".bmp"))) {
            try {
                Image image = ImageIO.read(Path.of(record.physicalPath).toFile());
                if (image != null) {
                    Image scaled = image.getScaledInstance(300, 210, Image.SCALE_SMOOTH);
                    previewIcon.setText("");
                    previewIcon.setIcon(new ImageIcon(scaled));
                } else previewIcon.setText("▧");
            } catch (IOException ex) { previewIcon.setText("▧"); }
        } else {
            previewIcon.setText(lower.endsWith(".pdf") ? "PDF" : (lower.matches(".*\\.(mp4|mkv|avi|mov)$") ? "▶" : "▤"));
            previewIcon.setFont(new Font("SansSerif", Font.BOLD, 62));
            previewIcon.setForeground(new Color(90, 165, 255));
        }
        previewHint.setText(record.physicalPath == null ? "Simulated content" : "Opens with your PC's default app");
        previewOpenButton.setVisible(true);
    // END: updatePreviewPanel
    }

    // START: refreshStorage - Recalculates logical storage usage and updates the storage meter.
    private void refreshStorage() {
        long used = fs.totalSize();
        long capacity = fs.capacityBytes;
        int percent = (int) Math.min(100, Math.round(100.0 * used / capacity));
        storageLabel.setText("Storage used: " + formatBytes(used) + " / " + formatBytes(capacity)
                + "     •     Files: " + fs.records.size() + "     •     Folders: " + (fs.folders.size() - 1));
        storageBar.setValue(percent);
        storageBar.setString(percent + "% used");
    // END: refreshStorage
    }

    // START: createFolder - Creates a folder in the current FileNest location.
    private void createFolder() {
        String name = JOptionPane.showInputDialog(frame, "Folder name:");
        if (name == null || name.isBlank()) return;
        try {
            fs.createFolder(currentFolderId, name.trim());
            refreshAll();
            status("Folder created.");
        } catch (RuntimeException ex) { error(ex.getMessage()); }
    // END: createFolder
    }

    // START: createFile - Creates a file record and registers its indexes and undo action.
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
    // END: createFile
    }

    // START: accessSelected - Records an access event for the selected FileNest file and updates LRU history.
    private void accessSelected() {
        FsNode node = selectedNode();
        if (!(node instanceof FileNode file)) { if (node != null) error("Select a file, not a folder."); return; }
        fs.access(file.id);
        accessHistory.addFirst(file.name + "  •  " + formatTime(Instant.now()));
        while (accessHistory.size() > 30) accessHistory.removeLast();
        refreshTable();
        status("Access recorded for " + file.name + " (LRU updated in O(1) average time).");
    // END: accessSelected
    }

    // START: previewSelected - Previews the selected supported file or opens it through the available file-opening flow.
    private void previewSelected() {
        int selectedRow = fileTable.getSelectedRow();
        if (selectedRow >= 0 && pcBrowsePath != null) {
            String key = String.valueOf(tableModel.getValueAt(fileTable.convertRowIndexToModel(selectedRow), 5));
            File entry = pcBrowseEntries.get(key);
            if (entry != null && entry.isFile()) openPcBrowserFile(entry);
            return;
        }
        FsNode node = selectedNode();
        if (node == null) return;

        if (node instanceof FolderNode folder) {
            String details = "Folder: " + folder.name
                    + "\nFiles inside: " + fs.countFiles(folder)
                    + "\nSubfolders: " + fs.countFolders(folder)
                    + "\nTotal size: " + formatBytes(fs.folderSize(folder));
            JOptionPane.showMessageDialog(frame, details, "Folder Details", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        FileRecord record = fs.records.get(node.id);
        if (record == null) { error("File details are unavailable."); return; }

        if (record.physicalPath != null) {
            openPhysicalFile(record);
            return;
        }

        JTextArea content = new JTextArea(record.content);
        content.setEditable(false);
        content.setLineWrap(true);
        content.setWrapStyleWord(true);
        content.setCaretPosition(0);
        content.setFont(new Font("Monospaced", Font.PLAIN, 13));

        JLabel details = new JLabel("  " + record.name + "     •     " + formatBytes(record.size)
                + "     •     Accessed " + record.accessCount + " time(s)");
        details.setBorder(new EmptyBorder(8, 4, 8, 4));

        JPanel previewPanel = new JPanel(new BorderLayout(6, 6));
        previewPanel.add(details, BorderLayout.NORTH);
        previewPanel.add(new JScrollPane(content), BorderLayout.CENTER);
        previewPanel.setPreferredSize(new Dimension(650, 380));
        JOptionPane.showMessageDialog(frame, previewPanel, "File Preview", JOptionPane.PLAIN_MESSAGE);
        status("Previewing " + record.name + ".");
    // END: previewSelected
    }

    // Lightweight heuristic scan: this is a warning aid, not a certified antivirus.
    private static final Set<String> RISKY_EXTENSIONS = Set.of(
            ".exe", ".dll", ".scr", ".com", ".msi", ".bat", ".cmd",
            ".ps1", ".vbs", ".js", ".hta", ".jar", ".reg", ".wsf");
    private static final Set<String> SCRIPT_EXTENSIONS = Set.of(
            ".bat", ".cmd", ".ps1", ".vbs", ".js", ".hta", ".wsf", ".sh", ".py");

    // START: scanSelection - Starts the selected-file or selected-folder content scan and displays findings.
    private void scanSelection() {
        // SCAN START: This method runs when the user clicks the Scan button.
        // It determines whether the user is browsing a real PC folder or FileNest's virtual tree,
        // sends the selected target to the appropriate recursive scanner, and displays warnings.
        // The scan reports suspicious indicators only; it does not claim to be a full antivirus.
        List<String> findings = new ArrayList<>();
        List<Path> cleanableScripts = new ArrayList<>();
        List<FileRecord> simulatedRecords = new ArrayList<>();
        int[] scanned = {0};
        String scanScope = "current folder";

        // STEP 1 - Determine the scan target and scan either the PC path or FileNest item.
        try {
            if (pcBrowsePath != null) {
                int row = fileTable.getSelectedRow();
                Path target = pcBrowsePath;
                if (row >= 0) {
                    String key = String.valueOf(tableModel.getValueAt(fileTable.convertRowIndexToModel(row), 5));
                    File entry = pcBrowseEntries.get(key);
                    if (entry != null) target = entry.toPath();
                }
                scanScope = target.toAbsolutePath().normalize().toString();
                scanPath(target, findings, cleanableScripts, scanned);
            } else {
                FsNode selected = null;
                int row = fileTable.getSelectedRow();
                if (row >= 0) {
                    String id = String.valueOf(tableModel.getValueAt(fileTable.convertRowIndexToModel(row), 5));
                    try { selected = fs.findNode(id); } catch (RuntimeException ignored) { }
                }
                if (selected == null) selected = fs.folders.get(currentFolderId);
                if (selected == null) selected = fs.root;
                scanScope = selected instanceof FolderNode folder
                        ? fs.folderPath(folder) : fs.folderPath(selected.parent) + "/" + selected.name;
                scanFileSystemNode(selected, findings, cleanableScripts, simulatedRecords, scanned);
            }
        } catch (RuntimeException ex) {
            error("Scan could not be completed: " + ex.getMessage());
            return;
        }

        // STEP 2 - Build a readable scan report with the number of checked files and warnings.
        StringBuilder report = new StringBuilder();
        report.append("Scan scope: ").append(scanScope)
              .append("\nOnly this selected item and its contents were scanned.\n\n")
              .append("Files checked: ").append(scanned[0])
              .append("\nPotential warnings: ").append(findings.size());
        if (findings.isEmpty()) {
            report.append("\n\nNo suspicious patterns were detected by FileNest's basic scanner.");
        } else {
            report.append("\n\nFindings (heuristic warnings):");
            int shown = Math.min(20, findings.size());
            for (int i = 0; i < shown; i++) report.append("\n• ").append(findings.get(i));
            if (findings.size() > shown) report.append("\n…and ").append(findings.size() - shown).append(" more.");
        }

        // STEP 3 - Offer separate cleaned copies for eligible scripts; original files are preserved.
        if (!cleanableScripts.isEmpty()) {
            report.append("\n\n").append(cleanableScripts.size())
                  .append(" script file(s) contain suspicious command lines.");
            report.append("\nFileNest can create cleaned copies with matching lines removed. Originals will not be changed.");
            int choice = JOptionPane.showConfirmDialog(frame, report.toString() + "\n\nCreate cleaned copies now?",
                    "FileNest Security Scan", JOptionPane.YES_NO_OPTION, findings.isEmpty()
                            ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) createCleanedCopies(cleanableScripts);
        } else {
            JOptionPane.showMessageDialog(frame, report.toString()
                    + "\n\nNo files were modified. Files that look risky need review with a trusted antivirus.",
                    "FileNest Security Scan", findings.isEmpty()
                            ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        }
        status("Security scan finished: " + scanned[0] + " file(s), " + findings.size() + " warning(s).");
        // SCAN END: findings have been shown and the scan status has been updated.
    // END: scanSelection
    }

    // SCAN HELPER: Recursively visits FileNest's virtual folder tree and checks each file record.
    private void scanFileSystemNode(FsNode node, List<String> findings, List<Path> cleanableScripts,
                                    List<FileRecord> simulatedRecords, int[] scanned) {
        if (node == null) return;
        if (node instanceof FileNode) {
            FileRecord record = fs.records.get(node.id);
            if (record == null) return;
            scanned[0]++;
            if (record.physicalPath != null && Files.isRegularFile(Path.of(record.physicalPath))) {
                scanSingleFile(Path.of(record.physicalPath), findings, cleanableScripts);
            } else {
                List<String> suspicious = suspiciousLines(record.content == null ? "" : record.content);
                String ext = extensionOf(record.name);
                if (RISKY_EXTENSIONS.contains(ext) || !suspicious.isEmpty()) {
                    findings.add(record.name + " — " + (suspicious.isEmpty()
                            ? "potentially executable file type" : "suspicious command/content pattern"));
                }
            }
            return;
        }
        if (node instanceof FolderNode folder) {
            for (FsNode child : folder.sortedChildren.values())
                scanFileSystemNode(child, findings, cleanableScripts, simulatedRecords, scanned);
        }
    }

    // START: scanPath - Recursively scans a path and gathers findings for supported files.
    // SCAN HELPER: Walks a real PC directory (up to the configured file limit) and checks each file.
    private void scanPath(Path target, List<String> findings, List<Path> cleanableScripts, int[] scanned) {
        if (Files.isRegularFile(target)) {
            scanned[0]++;
            scanSingleFile(target, findings, cleanableScripts);
            return;
        }
        if (!Files.isDirectory(target) || !Files.isReadable(target)) {
            findings.add(target + " — unavailable or unreadable");
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(target)) {
            paths.filter(Files::isRegularFile).limit(5000).forEach(path -> {
                scanned[0]++;
                scanSingleFile(path, findings, cleanableScripts);
            });
        } catch (IOException | SecurityException ex) {
            findings.add(target + " — could not fully scan: " + ex.getMessage());
        }
    // END: scanPath
    }

    // START: scanSingleFile - Reads and checks one file for the scan's configured indicators.
    // SCAN HELPER: Checks one physical file's extension and, for supported small scripts, its text.
    private void scanSingleFile(Path path, List<String> findings, List<Path> cleanableScripts) {
        String ext = extensionOf(path.getFileName().toString());
        boolean riskyType = RISKY_EXTENSIONS.contains(ext);
        List<String> suspicious = List.of();
        if (SCRIPT_EXTENSIONS.contains(ext)) {
            try {
                long size = Files.size(path);
                if (size <= 2L * 1024 * 1024) {
                    String content = Files.readString(path, StandardCharsets.UTF_8);
                    suspicious = suspiciousLines(content);
                    if (!suspicious.isEmpty()) cleanableScripts.add(path);
                }
            } catch (IOException | RuntimeException ex) {
                findings.add(path.getFileName() + " — script could not be inspected");
            }
        }
        if (riskyType || !suspicious.isEmpty()) {
            String reason = !suspicious.isEmpty() ? "suspicious script command(s) detected"
                    : "potentially executable file type (" + ext + ")";
            findings.add(path + " — " + reason);
        }
    // END: scanSingleFile
    }

    // START: suspiciousLines - Checks text lines against the scan's configured suspicious-pattern rules.
    // SCAN HELPER: Searches script text for configured suspicious command signatures.
    private List<String> suspiciousLines(String content) {
        List<String> matches = new ArrayList<>();
        if (content == null || content.isBlank()) return matches;
        String[] signatures = {
            "powershell -enc", "frombase64string", "downloadstring(",
            "invoke-expression", "iex(", "certutil -decode", "bitsadmin ",
            "mshta ", "wscript.shell", "createobject(", "rundll32 ",
            "regsvr32 ", "cmd /c", "start-process"
        };
        for (String line : content.split("\\R", -1)) {
            String lower = line.toLowerCase(Locale.ROOT);
            for (String signature : signatures) {
                if (lower.contains(signature)) {
                    matches.add(line);
                    break;
                }
            }
        }
        return matches;
    // END: suspiciousLines
    }

    // START: extensionOf - Handles the extensionOf operation in the FileNest application.
    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
    // END: extensionOf
    }

    // START: createCleanedCopies - Creates separate cleaned copies for eligible scanned scripts; it does not overwrite originals.
    private void createCleanedCopies(List<Path> scripts) {
        Path output = Path.of(System.getProperty("user.home"), "FileNest_Cleaned");
        int created = 0;
        try {
            Files.createDirectories(output);
            for (Path source : scripts) {
                if (!Files.isRegularFile(source) || Files.size(source) > 2L * 1024 * 1024) continue;
                String original = Files.readString(source, StandardCharsets.UTF_8);
                List<String> kept = new ArrayList<>();
                for (String line : original.split("\\R", -1)) {
                    if (suspiciousLines(line).isEmpty()) kept.add(line);
                }
                String cleaned = String.join(System.lineSeparator(), kept);
                if (cleaned.equals(original)) continue;
                String base = source.getFileName().toString();
                int dot = base.lastIndexOf('.');
                String name = dot > 0 ? base.substring(0, dot) + "_cleaned" + base.substring(dot)
                        : base + "_cleaned";
                Path destination = output.resolve(name);
                int suffix = 1;
                while (Files.exists(destination)) {
                    String candidate = dot > 0 ? base.substring(0, dot) + "_cleaned_" + suffix + base.substring(dot)
                            : base + "_cleaned_" + suffix;
                    destination = output.resolve(candidate);
                    suffix++;
                }
                Files.writeString(destination, cleaned, StandardCharsets.UTF_8);
                created++;
            }
            JOptionPane.showMessageDialog(frame, created + " cleaned copy/copies created in:\n" + output
                    + "\n\nOriginal files were left unchanged. Review each copy before using it; automatic line removal can break scripts.",
                    "Cleaned copies created", JOptionPane.INFORMATION_MESSAGE);
            status(created + " cleaned copy/copies saved to " + output);
        } catch (IOException | SecurityException ex) {
            error("Could not create cleaned copies: " + ex.getMessage());
        }
    // END: createCleanedCopies
    }

    // START: importFiles - Imports selected PC files using either Copy into FileNest or Direct Access mode.
    private void importFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select files to add to FileNest");
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        File[] selected = chooser.getSelectedFiles();
        if (selected.length == 0 && chooser.getSelectedFile() != null) selected = new File[]{chooser.getSelectedFile()};
        Object[] choices = {"Copy into FileNest", "Keep original location (Direct Access)", "Cancel"};
        int choice = JOptionPane.showOptionDialog(frame,
                "How should FileNest manage these " + selected.length + " file(s)?",
                "Import mode", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, choices, choices[0]);
        if (choice < 0 || choice == 2) return;
        boolean copy = choice == 0;

        // Best-effort batch: one failed file must not cancel the remaining imports.
        int added = 0;
        int skipped = 0;
        List<String> failures = new ArrayList<>();
        for (File source : selected) {
            if (source == null || !source.isFile()) {
                failures.add((source == null ? "(unknown file)" : source.getName())
                        + " — unavailable or not a regular file");
                continue;
            }
            int previousCount = fs.records.size();
            try {
                fs.addPhysicalFile(currentFolderId, source.toPath(), copy);
                if (fs.records.size() > previousCount) added++;
                else skipped++; // A direct-access path may already be registered.
            } catch (IOException | RuntimeException ex) {
                String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                failures.add(source.getName() + " — " + reason);
            }
        }

        refreshAll();
        StringBuilder summary = new StringBuilder()
                .append("Added: ").append(added)
                .append("\nSkipped / already registered: ").append(skipped)
                .append("\nFailed: ").append(failures.size());
        int shown = Math.min(12, failures.size());
        if (shown > 0) {
            summary.append("\n\nFiles that could not be imported:");
            for (int i = 0; i < shown; i++) summary.append("\n• ").append(failures.get(i));
            if (failures.size() > shown) summary.append("\n…and ").append(failures.size() - shown).append(" more.");
        }
        JOptionPane.showMessageDialog(frame, summary.toString(), "Import results",
                failures.isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        status("Import finished: " + added + " added, " + skipped + " skipped, "
                + failures.size() + " failed.");
    // END: importFiles
    }

    // START: openPhysicalFile - Opens an imported physical file with the operating system's default application.
    private void openPhysicalFile(FileRecord record) {
        try {
            Path path = Path.of(record.physicalPath);
            if (!Files.exists(path)) {
                error("This file is no longer available at its saved location:\n" + path);
                return;
            }
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                error("Windows default-app opening is not supported on this computer.");
                return;
            }
            Desktop.getDesktop().open(path.toFile());
            fs.access(record.id);
            accessHistory.addFirst(record.name + "  •  " + formatTime(Instant.now()));
            while (accessHistory.size() > 30) accessHistory.removeLast();
            refreshTable();
            status("Opened " + record.name + " in its default application; access recorded.");
        } catch (IOException | UnsupportedOperationException ex) {
            error("Could not open file: " + ex.getMessage());
        }
    // END: openPhysicalFile
    }

    // START: openPcBrowserFile - Opens a file selected while browsing a real PC folder.
    private void openPcBrowserFile(File file) {
        try {
            if (!file.exists() || !file.isFile()) { error("This file is no longer available."); return; }
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                error("Opening files with Windows default applications is not supported on this computer.");
                return;
            }
            Desktop.getDesktop().open(file);
            accessHistory.addFirst(file.getName() + "  •  " + formatTime(Instant.now()) + "  •  PC direct");
            while (accessHistory.size() > 30) accessHistory.removeLast();
            status("Opened directly from PC; no copy was saved in FileNest: " + file.getName());
        } catch (IOException | UnsupportedOperationException ex) {
            error("Could not open file: " + ex.getMessage());
        }
    // END: openPcBrowserFile
    }

    // START: selectedNode - Handles the selectedNode operation in the FileNest application.
    private FsNode selectedNode() {
        int row = fileTable.getSelectedRow();
        if (row < 0) { error("Select a file or folder first."); return null; }
        String rowKey = String.valueOf(tableModel.getValueAt(fileTable.convertRowIndexToModel(row), 5));
        if (rowKey.startsWith("PCFS:")) {
            error("This is a directly browsed PC item. Import it first if you want to manage it inside FileNest.");
            return null;
        }
        String name = String.valueOf(tableModel.getValueAt(row, 0));
        String id = String.valueOf(tableModel.getValueAt(row, 5));
        try { return fs.findNode(id); }
        catch (RuntimeException ex) { error(ex.getMessage()); return null; }
    // END: selectedNode
    }

    // START: renameSelected - Renames the selected virtual FileNest entry.
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
    // END: renameSelected
    }

    // START: moveSelected - Moves the selected virtual FileNest entry to another FileNest folder.
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
    // END: moveSelected
    }

    // START: deleteSelected - Deletes the selected virtual entry and records the operation for undo.
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
    // END: deleteSelected
    }

    // START: searchFiles - Runs filename prefix search using the Trie index.
    private void searchFiles() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) { refreshTable(); return; }
        List<FileRecord> results = fs.searchPrefix(query);
        tableModel.setRowCount(0);
        for (FileRecord r : results) {
            tableModel.addRow(new Object[]{r.name, r.physicalPath == null ? "File" : (r.managedCopy ? "PC Copy" : "PC Link"), formatBytes(r.size), formatTime(r.lastAccess), r.accessCount, r.id});
        }
        status(results.size() + " result(s) for prefix \"" + query + "\" (Trie search).");
    // END: searchFiles
    }

    // START: showCleanup - Displays least-recently-accessed cleanup suggestions without deleting files automatically.
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
    // END: showCleanup
    }

    // START: showHistory - Displays the file access history in least-recently-used order.
    private void showHistory() {
        List<FileRecord> ordered = fs.lruSnapshot();
        StringBuilder b = new StringBuilder("Least recently used → Most recently used\n\n");
        for (int i=0; i<ordered.size(); i++) b.append(i+1).append(". ").append(ordered.get(i).name)
                .append(" — ").append(formatTime(ordered.get(i).lastAccess)).append('\n');
        b.append("\nRecent access events:\n");
        for (String event : accessHistory) b.append("• ").append(event).append('\n');
        JOptionPane.showMessageDialog(frame, b.toString(), "LRU & Access History", JOptionPane.INFORMATION_MESSAGE);
    // END: showHistory
    }

    // START: showAnalytics - Calculates and displays storage, file, folder, and duplicate analytics.
    private void showAnalytics() {
        // ANALYSIS START: This method runs when the user clicks the Analyze button.
        // It gathers statistics for the selected file/folder (or the current folder by default),
        // calculates storage usage, finds large/old/duplicate files and empty folders,
        // runs the basic safety scan, then builds and displays the Folder Health Report.
        // STEP 1 - Select the analysis target.
        FsNode target = null;
        int selectedRow = fileTable == null ? -1 : fileTable.getSelectedRow();
        if (selectedRow >= 0 && pcBrowsePath == null) {
            String id = String.valueOf(tableModel.getValueAt(fileTable.convertRowIndexToModel(selectedRow), 5));
            try { target = fs.findNode(id); } catch (RuntimeException ignored) { }
        }
        if (target == null) target = fs.folders.get(currentFolderId);
        if (target == null) target = fs.root;

        // STEP 2 - Collect all files and subfolders inside the selected analysis target.
        List<FileRecord> scopedFiles = new ArrayList<>();
        List<FolderNode> scopedFolders = new ArrayList<>();
        if (target instanceof FileNode) {
            FileRecord record = fs.records.get(target.id);
            if (record != null) scopedFiles.add(record);
        } else if (target instanceof FolderNode folder) {
            collectAnalysisContents(folder, scopedFiles, scopedFolders);
        }

        // STEP 3 - CALCULATE ANALYTICS: total size, capacity percentage and largest files.
        long used = scopedFiles.stream().mapToLong(r -> r.size).sum();
        int percent = (int) Math.min(100, Math.round(100.0 * used / fs.capacityBytes));
        List<FileRecord> largest = new ArrayList<>(scopedFiles);
        largest.sort(Comparator.comparingLong((FileRecord r) -> r.size).reversed());
        // STEP 4 - Find files whose last recorded access is at least 30 days old.
        Instant oldCutoff = Instant.now().minus(Duration.ofDays(30));
        List<FileRecord> oldFiles = new ArrayList<>();
        for (FileRecord record : scopedFiles) {
            if (!record.lastAccess.isAfter(oldCutoff)) oldFiles.add(record);
        }
        oldFiles.sort(Comparator.comparing(r -> r.lastAccess));

        // STEP 5 - Group files by their SHA-256 fingerprint to identify matching content.
        Map<String, List<FileRecord>> duplicateGroups = new LinkedHashMap<>();
        for (FileRecord record : scopedFiles)
            duplicateGroups.computeIfAbsent(record.fingerprint, key -> new ArrayList<>()).add(record);
        int duplicateEntries = 0;
        for (List<FileRecord> group : duplicateGroups.values())
            if (group.size() > 1) duplicateEntries += group.size();

        List<FolderNode> emptyFolders = new ArrayList<>();
        for (FolderNode folder : scopedFolders)
            if (folder != target && folder.children.isEmpty()) emptyFolders.add(folder);

        // STEP 6 - BASIC SAFETY SCAN: analysis calls the scanner to check this same target.
        // This is a heuristic check, not a full antivirus scan.
        List<String> securityFindings = new ArrayList<>();
        int[] scanned = {0};
        try {
            scanFileSystemNode(target, securityFindings, new ArrayList<>(), new ArrayList<>(), scanned);
        } catch (RuntimeException ex) {
            securityFindings.add("Some items could not be checked: " + ex.getMessage());
        }

        // STEP 7 - BUILD REPORT: format the calculated results as an HTML report for the GUI.
        String locationName = target.name;
        String locationPath = target instanceof FolderNode folder ? fs.folderPath(folder) : fs.folderPath(target.parent) + "/" + target.name;
        StringBuilder html = new StringBuilder("<html><body style='font-family:Segoe UI,Arial; color:#26364a;'>");
        html.append("<h1 style='color:#1769c2;'>Folder Health Report</h1>");
        html.append("<p style='color:#62758b;'>Analysis for <b>").append(escapeHtml(locationName))
                .append("</b><br>").append(escapeHtml(locationPath))
                .append("<br>Only this selected item and its contents were checked.</p>");

        html.append("<h2>At a glance</h2><table cellpadding='8' cellspacing='0' style='background:#eef5fc;width:100%;'>");
        html.append("<tr><td><b>").append(scopedFiles.size()).append("</b><br>Files</td><td><b>")
                .append(Math.max(0, scopedFolders.size() - (target instanceof FolderNode ? 1 : 0)))
                .append("</b><br>Subfolders</td><td><b>").append(escapeHtml(formatBytes(used)))
                .append("</b><br>Space used</td></tr></table>");
        html.append("<p><b>").append(percent).append("%</b> of FileNest's 10 GB capacity, based on this location's files.</p>");

        html.append("<h2>What may need attention?</h2><ul>");
        long largeCount = largest.stream().filter(r -> r.size >= 100L * 1024 * 1024).count();
        html.append("<li><b>Possible duplicates:</b> ").append(duplicateEntries)
                .append(" file entries share matching content within this location.</li>");
        html.append("<li><b>Large files:</b> ").append(largeCount)
                .append(" files are 100 MB or larger.</li>");
        html.append("<li><b>Not opened recently:</b> ").append(oldFiles.size())
                .append(" files have no recorded access in the last 30 days.</li>");
        html.append("<li><b>Empty subfolders:</b> ").append(emptyFolders.size()).append(".</li>");
        html.append("</ul>");

        html.append("<h3>Largest files here</h3>");
        if (largest.isEmpty()) html.append("<p>This location has no files yet.</p>");
        else {
            html.append("<ul>");
            for (int i = 0; i < Math.min(5, largest.size()); i++) {
                FileRecord file = largest.get(i);
                html.append("<li>").append(escapeHtml(file.name)).append(" — ")
                        .append(escapeHtml(formatBytes(file.size))).append("</li>");
            }
            html.append("</ul>");
        }

        html.append("<h3>Files to review</h3>");
        if (oldFiles.isEmpty()) html.append("<p>No files in this location meet the 30-day check.</p>");
        else {
            html.append("<ul>");
            for (int i = 0; i < Math.min(5, oldFiles.size()); i++) {
                FileRecord file = oldFiles.get(i);
                html.append("<li>").append(escapeHtml(file.name)).append(" — last recorded access ")
                        .append(escapeHtml(formatTime(file.lastAccess))).append("</li>");
            }
            html.append("</ul>");
        }

        html.append("<h2>Basic safety check</h2><p>Files checked: <b>").append(scanned[0])
                .append("</b> &nbsp; | &nbsp; Warnings: <b>").append(securityFindings.size()).append("</b></p>");
        if (securityFindings.isEmpty()) {
            html.append("<p style='color:#18794e;'>No suspicious patterns were found by FileNest's basic check in this location.</p>");
        } else {
            html.append("<p style='color:#a15c00;'>Please review these items before opening them:</p><ul>");
            for (int i = 0; i < Math.min(5, securityFindings.size()); i++)
                html.append("<li>").append(escapeHtml(securityFindings.get(i))).append("</li>");
            html.append("</ul>");
        }
        html.append("<p style='color:#68798c;font-size:small;'>This is a basic warning system, not a full antivirus. A clean result does not guarantee a file is safe.</p>");

        html.append("<h2>Suggested next steps</h2><ul>");
        boolean adviceAdded = false;
        if (percent >= 80) {
            html.append("<li>This location uses a large share of FileNest's capacity. Review the largest files before adding more.</li>");
            adviceAdded = true;
        }
        if (duplicateEntries > 0) {
            html.append("<li>Compare the possible duplicate files and keep the copies you need.</li>");
            adviceAdded = true;
        }
        if (!oldFiles.isEmpty()) {
            html.append("<li>Review older files. Archive or delete only files you no longer need.</li>");
            adviceAdded = true;
        }
        if (!emptyFolders.isEmpty()) {
            html.append("<li>Check empty subfolders and remove only those you no longer need.</li>");
            adviceAdded = true;
        }
        if (!securityFindings.isEmpty()) {
            html.append("<li>Review the safety warnings before opening unfamiliar files.</li>");
            adviceAdded = true;
        }
        if (!adviceAdded) html.append("<li>No major issues were found in the checks for this location.</li>");
        html.append("</ul>");
        html.append("<p style='color:#68798c;font-size:small;'>This report only reads information. It does not delete, move, quarantine, or modify files.</p>");
        html.append("</body></html>");

        JEditorPane report = new JEditorPane("text/html", html.toString());
        report.setEditable(false);
        report.setCaretPosition(0);
        report.setBackground(Color.WHITE);
        report.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        JScrollPane scroll = new JScrollPane(report);
        scroll.setPreferredSize(new Dimension(700, 620));
        JOptionPane.showMessageDialog(frame, scroll, "Analysis: " + locationName,
                JOptionPane.INFORMATION_MESSAGE);
        status("Analysis completed for " + locationName + " (" + scopedFiles.size() + " file(s)).");
        // ANALYSIS END: the report has been displayed and the status bar has been updated.
    // END: showAnalytics
    }

    // START: collectAnalysisContents - Collects file and folder records recursively for analytics.
    private void collectAnalysisContents(FolderNode folder, List<FileRecord> files, List<FolderNode> folders) {
        folders.add(folder);
        for (FsNode child : folder.sortedChildren.values()) {
            if (child instanceof FileNode) {
                FileRecord record = fs.records.get(child.id);
                if (record != null) files.add(record);
            } else if (child instanceof FolderNode childFolder) {
                collectAnalysisContents(childFolder, files, folders);
            }
        }
    // END: collectAnalysisContents
    }

    // START: escapeHtml - Handles the escapeHtml operation in the FileNest application.
    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#39;");
    // END: escapeHtml
    }

    // START: collectEmptyFolders - Finds empty folders recursively for the analytics report.
    private void collectEmptyFolders(FolderNode folder, List<FolderNode> result) {
        if (folder != fs.root && folder.children.isEmpty()) result.add(folder);
        for (FsNode child : folder.sortedChildren.values()) {
            if (child instanceof FolderNode childFolder) collectEmptyFolders(childFolder, result);
        }
    // END: collectEmptyFolders
    }

    // START: saveSnapshot - Saves the current FileNest state to a versioned .fns snapshot.
    private void saveSnapshot() {
        JFileChooser chooser = new JFileChooser(); chooser.setSelectedFile(new java.io.File("filenest-save.fns"));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        try { fs.save(chooser.getSelectedFile()); status("Saved FileNest snapshot."); }
        catch (IOException ex) { error("Could not save: " + ex.getMessage()); }
    // END: saveSnapshot
    }

    // START: loadSnapshot - Loads and validates a .fns snapshot before replacing the current in-memory state.
    private void loadSnapshot() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        try { fs.load(chooser.getSelectedFile()); currentFolderId="root"; accessHistory.clear(); refreshAll(); status("Loaded FileNest snapshot."); }
        catch (IOException | RuntimeException ex) { error("Could not load: " + ex.getMessage()); }
    // END: loadSnapshot
    }

    private void status(String text) { statusLabel.setText(text); }
    private void error(String text) { JOptionPane.showMessageDialog(frame, text, "FileNest", JOptionPane.WARNING_MESSAGE); }
    // START: formatBytes - Handles the formatBytes operation in the FileNest application.
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024));
    // END: formatBytes
    }
    // START: formatTime - Handles the formatTime operation in the FileNest application.
    private static String formatTime(Instant time) {
        return DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
                .withZone(ZoneId.systemDefault()).format(time);
    // END: formatTime
    }

    // START: FolderRef - Handles the FolderRef operation in the FileNest application.
    private record FolderRef(String id, String name) {
        @Override public String toString() { return name; }
    // END: FolderRef
    }

    // ---------------------- DSA CORE ----------------------

    private abstract static class FsNode {
        String id, name;
        FolderNode parent;
        FsNode(String id, String name, FolderNode parent) { this.id=id; this.name=name; this.parent=parent; }
    }

    /** N-ary tree node. HashMap-like child index gives average O(1) child lookup. */
    private static class FolderNode extends FsNode {
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

    private static class FileNode extends FsNode {
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

    private static class FileRecord {
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

    private static class FileSystemManager {
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
