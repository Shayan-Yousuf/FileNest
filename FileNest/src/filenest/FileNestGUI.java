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
public class FileNestGUI {
    final FileNestApp controller;
    JFrame frame;
    JTree folderTree;
    DefaultTreeModel treeModel;
    DefaultTableModel tableModel;
    JTable fileTable;
    JLabel statusLabel, storageLabel;
    JProgressBar storageBar;
    JTextField searchField;
    JLabel pathLabel, previewIcon, previewName, previewMeta, previewHint;
    JButton previewOpenButton;
    JButton themeToggleButton;
    boolean darkTheme = true;
    final Map<String, ImageIcon> thumbnailCache = new HashMap<>();
    FileNestGUI(FileNestApp controller) { this.controller = controller; }

    void show() {
        frame = new JFrame("FileNest | Real + Simulated File Manager");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1000, 650));
        frame.getContentPane().setBackground(new Color(29, 29, 29));
        frame.setLayout(new BorderLayout());
        frame.add(buildHeader(), BorderLayout.NORTH);
        frame.add(buildMain(), BorderLayout.CENTER);
        frame.add(buildStatus(), BorderLayout.SOUTH);
        applyTheme();
        controller.refreshAll();
        frame.setLocationByPlatform(true);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setVisible(true);
        frame.addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override public void windowGainedFocus(java.awt.event.WindowEvent e) {
                if (controller.pcBrowsePath != null && Files.isDirectory(controller.pcBrowsePath)) {
                    SwingUtilities.invokeLater(() -> {
                        controller.refreshTable();
                        fileTable.revalidate();
                        fileTable.repaint();
                    });
                }
            }
        });
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setBorder(new EmptyBorder(12, 16, 12, 16));
        header.setBackground(new Color(31, 31, 31));

        JLabel brand = new JLabel("▣  FileNest");
        brand.setForeground(new Color(245, 245, 245));
        brand.setFont(new Font("SansSerif", Font.BOLD, 21));
        header.add(brand, BorderLayout.WEST);

        JPanel address = new JPanel(new BorderLayout(8, 0));
        address.setBackground(new Color(43, 43, 43));
        address.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(55, 55, 55)),
                new EmptyBorder(8, 14, 8, 14)));
        pathLabel = new JLabel("Home  /  All files");
        pathLabel.setForeground(new Color(205, 205, 205));
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
        searchButton.addActionListener(e -> controller.searchFiles());
        searchField.addActionListener(e -> controller.searchFiles());
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
    }

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
    }

    private JComponent buildSidebar() {
        JPanel panel = new JPanel(new BorderLayout(8, 10));
        panel.setBorder(new EmptyBorder(18, 12, 14, 10));
        panel.setBackground(new Color(36, 36, 36));
        panel.setMinimumSize(new Dimension(190, 300));
        panel.setPreferredSize(new Dimension(218, 600));

        JPanel upper = new JPanel();
        upper.setOpaque(false);
        upper.setLayout(new BoxLayout(upper, BoxLayout.Y_AXIS));
        JLabel tabs = sidebarHeading("WORKSPACE");
        upper.add(tabs);
        upper.add(sidebarButton("⌂   Home", () -> { controller.pcBrowsePath = null; controller.currentFolderId = "root"; controller.refreshAll(); }));
        upper.add(sidebarButton("＋   Import files", controller::importFiles));
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

        DefaultMutableTreeNode root = new DefaultMutableTreeNode(new FileNestApp.FolderRef("root", "⌂  Home"));
        folderTree = new JTree(root);
        folderTree.setRootVisible(true);
        folderTree.setShowsRootHandles(true);
        folderTree.setRowHeight(27);
        folderTree.setBackground(new Color(37, 37, 37));
        folderTree.setForeground(new Color(135, 190, 250));
        folderTree.setFont(new Font("SansSerif", Font.PLAIN, 12));
        folderTree.setBorder(new EmptyBorder(5, 2, 5, 2));
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
        });
        folderTree.addTreeSelectionListener(e -> {
            Object node = folderTree.getLastSelectedPathComponent();
            if (node instanceof DefaultMutableTreeNode dm && dm.getUserObject() instanceof FileNestApp.FolderRef ref) {
                controller.pcBrowsePath = null;
                controller.currentFolderId = ref.id();
                controller.refreshTable();
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
        newFolder.addActionListener(e -> controller.createFolder());
        JButton newFile = createButton("+ New File", false);
        newFile.addActionListener(e -> controller.createFile());
        actions.add(newFolder);
        actions.add(newFile);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JLabel sidebarHeading(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(145, 145, 145));
        label.setFont(new Font("SansSerif", Font.BOLD, 10));
        label.setBorder(new EmptyBorder(9, 8, 6, 4));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JButton sidebarButton(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setForeground(new Color(215, 215, 215));
        button.setBackground(new Color(37, 37, 37));
        button.setBorder(new EmptyBorder(8, 9, 8, 5));
        button.setFocusPainted(false);
        button.setFont(new Font("SansSerif", Font.PLAIN, 12));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.addActionListener(e -> action.run());
        return button;
    }

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
    }

    private boolean isImageFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.matches(".*\\.(png|jpe?g|gif|bmp|wbmp|tif|tiff)$");
    }

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
    }

    private void browseDrive(File drive) {
        browsePcFolder(drive.toPath());
    }

    private void browsePcFolder(Path directory) {
        if (!Files.isDirectory(directory) || !Files.isReadable(directory)) {
            controller.error("This folder is unavailable or you do not have permission to access it.");
            return;
        }
        controller.pcBrowsePath = directory.toAbsolutePath().normalize();
        controller.currentFolderId = "root";
        if (fileTable != null) fileTable.clearSelection();
        controller.pcBrowseEntries.clear();
        if (pathLabel != null) pathLabel.setText(controller.pcBrowsePath.toString());
        controller.refreshTable();
        if (fileTable != null) {
            fileTable.revalidate();
            fileTable.repaint();
        }
        controller.updatePreviewPanel();
        controller.status("Browsing PC folder directly: " + controller.pcBrowsePath);
    }

    private void choosePcFolder(String folderName) {
        browsePcFolder(Path.of(System.getProperty("user.home"), folderName));
    }

    private JComponent buildWorkspace() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 12, 12));
        panel.setBackground(new Color(29, 29, 29));

        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.setOpaque(false);
        JPanel headingRow = new JPanel(new BorderLayout());
        headingRow.setOpaque(false);
        JLabel heading = new JLabel("Browse files");
        heading.setForeground(new Color(238, 238, 238));
        heading.setFont(new Font("SansSerif", Font.BOLD, 18));
        headingRow.add(heading, BorderLayout.WEST);
        JLabel hint = new JLabel("Select a file to see its preview");
        hint.setForeground(new Color(145, 145, 145));
        hint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        headingRow.add(hint, BorderLayout.EAST);
        top.add(headingRow, BorderLayout.NORTH);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);
        JButton[] buttons = {
            createButton("Import", false), createButton("Rename", false),
            createButton("Move", false), createButton("Delete", false),
            createButton("Undo", false), createButton("Cleanup", false),
            createButton("History", false), createButton("Analyze", false),
            createButton("Scan", true), createButton("Save", false), createButton("Load", false)
        };
        buttons[0].addActionListener(e -> controller.importFiles());
        buttons[1].addActionListener(e -> controller.renameSelected());
        buttons[2].addActionListener(e -> controller.moveSelected());
        buttons[3].addActionListener(e -> controller.deleteSelected());
        buttons[4].addActionListener(e -> { if (controller.fs.canUndo()) { controller.fs.undo(); controller.refreshAll(); controller.status("Last operation undone."); } else controller.status("Nothing to undo yet."); });
        buttons[5].addActionListener(e -> controller.showCleanup());
        buttons[6].addActionListener(e -> controller.showHistory());
        buttons[7].addActionListener(e -> controller.showAnalytics());
        buttons[8].addActionListener(e -> controller.scanSelection());
        buttons[9].addActionListener(e -> controller.saveSnapshot());
        buttons[10].addActionListener(e -> controller.loadSnapshot());
        for (JButton button : buttons) actions.add(button);
        top.add(actions, BorderLayout.CENTER);
        panel.add(top, BorderLayout.NORTH);

        String[] columns = {"Name", "Type", "Size", "Last Accessed", "Access Count", "ID"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        fileTable = new JTable(tableModel);
        fileTable.setRowHeight(43);
        fileTable.setFillsViewportHeight(true);
        fileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileTable.setShowGrid(false);
        fileTable.setIntercellSpacing(new Dimension(0, 0));
        fileTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        fileTable.setBackground(new Color(43, 43, 43));
        fileTable.setForeground(new Color(225, 225, 225));
        fileTable.setSelectionBackground(new Color(65, 65, 65));
        fileTable.setSelectionForeground(Color.WHITE);
        fileTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        fileTable.setRowSelectionAllowed(true);
        fileTable.setGridColor(new Color(48, 48, 48));
        fileTable.setTableHeader(null);
        fileTable.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                    boolean focused, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, selected, focused, row, column);
                label.setBorder(new EmptyBorder(2, 8, 2, 4));
                label.setIcon(null);
                label.setIconTextGap(10);
                int modelRow = table.convertRowIndexToModel(row);
                String key = String.valueOf(table.getModel().getValueAt(modelRow, 5));
                File imageFile = controller.pcBrowseEntries.get(key);
                if (imageFile != null && imageFile.isFile()) {
                    ImageIcon thumbnail = getThumbnail(imageFile, 38, 38);
                    if (thumbnail != null) label.setIcon(thumbnail);
                }
                return label;
            }
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
                FileNestApp.FsNode node = null;
                String rowId = String.valueOf(idValue);
                try { node = controller.fs.findNode(rowId); } catch (RuntimeException ignored) { }
                File pcEntry = controller.pcBrowseEntries.get(rowId);
                boolean folder = pcEntry != null && pcEntry.isDirectory() || node instanceof FileNestApp.FolderNode;
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
            if (!e.getValueIsAdjusting()) controller.updatePreviewPanel();
        });
        fileTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    if (controller.pcBrowsePath != null) {
                        int row = fileTable.getSelectedRow();
                        if (row >= 0) {
                            String key = String.valueOf(tableModel.getValueAt(fileTable.convertRowIndexToModel(row), 5));
                            File entry = controller.pcBrowseEntries.get(key);
                            if (entry != null && entry.isDirectory()) browsePcFolder(entry.toPath());
                            else if (entry != null) controller.openPcBrowserFile(entry);
                        }
                    } else {
                        FileNestApp.FsNode selected = selectedNode();
                        if (selected instanceof FileNestApp.FolderNode folder) {
                            controller.currentFolderId = folder.id;
                            controller.refreshAll();
                        } else if (selected instanceof FileNestApp.FileNode) controller.previewSelected();
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
        previewCard.setBorder(new EmptyBorder(18, 18, 18, 18));
        JLabel previewHeading = new JLabel("PREVIEW");
        previewHeading.setForeground(new Color(155, 155, 155));
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
        previewName.setForeground(new Color(235, 235, 235));
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
        previewOpenButton.addActionListener(e -> controller.previewSelected());
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
        storageLabel.setForeground(new Color(175, 175, 175));
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
    }

    private JButton createButton(String text, boolean primary) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? new Color(65, 145, 235) : new Color(68, 75, 86)),
                new EmptyBorder(7, 11, 7, 11)));
        button.setFont(new Font("SansSerif", Font.BOLD, 11));
        button.setBackground(primary ? new Color(65, 145, 235) : new Color(49, 54, 63));
        button.setForeground(primary ? Color.WHITE : new Color(135, 190, 250));
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private void styleInput(JTextField field) {
        field.setBackground(new Color(43, 43, 43));
        field.setForeground(new Color(235, 235, 235));
        field.setCaretColor(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(65, 65, 65)),
                new EmptyBorder(7, 9, 7, 9)));
    }

    private JPanel darkCard(LayoutManager layout) {
        JPanel card = new JPanel(layout);
        card.setBackground(new Color(43, 43, 43));
        card.setBorder(BorderFactory.createEmptyBorder());
        return card;
    }

    private void applyTheme() {
        if (frame == null) return;
        Color background = darkTheme ? new Color(29, 29, 29) : new Color(242, 246, 251);
        Color panel = darkTheme ? new Color(36, 36, 36) : new Color(255, 255, 255);
        Color surface = darkTheme ? new Color(43, 43, 43) : new Color(250, 252, 255);
        Color foreground = darkTheme ? new Color(225, 230, 238) : new Color(35, 48, 65);
        Color muted = darkTheme ? new Color(155, 165, 178) : new Color(100, 115, 132);
        Color border = darkTheme ? new Color(55, 62, 72) : new Color(215, 224, 235);
        applyThemeTo(frame.getContentPane(), background, panel, surface, foreground, muted, border);
        frame.getContentPane().setBackground(background);
        if (themeToggleButton != null) {
            themeToggleButton.setText(darkTheme ? "☀ Light mode" : "☾ Dark mode");
            themeToggleButton.setBackground(new Color(45, 125, 210));
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
            storageBar.setForeground(new Color(45, 135, 220));
        }
        frame.repaint();
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
                button.setBackground(darkTheme ? new Color(49, 54, 63) : new Color(231, 239, 248));
                button.setForeground(darkTheme ? new Color(150, 195, 245) : new Color(35, 105, 175));
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

    private void styleScrollPane(JScrollPane scroll) {
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setBackground(new Color(43, 43, 43));
        scroll.getViewport().setBackground(new Color(43, 43, 43));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().setBackground(new Color(43, 43, 43));
        scroll.getVerticalScrollBar().setForeground(new Color(65, 145, 235));
        scroll.getHorizontalScrollBar().setBackground(new Color(43, 43, 43));
    }

    private void JTableHeaderStyle(JTable table) {
        table.getTableHeader().setBackground(new Color(37, 37, 37));
        table.getTableHeader().setForeground(new Color(165, 165, 165));
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 10));
        table.getTableHeader().setPreferredSize(new Dimension(0, 34));
        table.getTableHeader().setReorderingAllowed(false);
    }

    private JComponent buildStatus() {
        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(new EmptyBorder(7, 14, 7, 14));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(new Color(27, 27, 27));
        statusLabel.setForeground(new Color(155, 155, 155));
        return statusLabel;
    }

}
