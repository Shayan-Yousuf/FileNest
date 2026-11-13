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
    private String currentFolderId = "root";
    private final Deque<String> accessHistory = new ArrayDeque<>();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FileNestApp().show());
    }

    private void show() {
        fs.seedDemoData();
        frame = new JFrame("FileNest | Real + Simulated File Manager");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1000, 650));
        frame.getContentPane().setBackground(new Color(29, 29, 29));
        frame.setLayout(new BorderLayout());
        frame.add(buildHeader(), BorderLayout.NORTH);
        frame.add(buildMain(), BorderLayout.CENTER);
        frame.add(buildStatus(), BorderLayout.SOUTH);
        refreshAll();
        frame.setLocationByPlatform(true);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setVisible(true);
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
        searchButton.addActionListener(e -> searchFiles());
        searchField.addActionListener(e -> searchFiles());
        search.add(searchField, BorderLayout.CENTER);
        search.add(searchButton, BorderLayout.EAST);
        header.add(search, BorderLayout.EAST);
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
        upper.add(sidebarButton("⌂   Home", () -> { currentFolderId = "root"; refreshAll(); }));
        upper.add(sidebarButton("＋   Add files from PC", this::browseAndOpenPcFile));
        upper.add(Box.createVerticalStrut(15));
        upper.add(sidebarHeading("PINNED"));
        upper.add(sidebarButton("▣   Downloads", () -> choosePcFolder("Downloads")));
        upper.add(sidebarButton("▣   Documents", () -> choosePcFolder("Documents")));
        upper.add(sidebarButton("▧   Pictures", () -> choosePcFolder("Pictures")));
        upper.add(sidebarButton("▣   Desktop", () -> choosePcFolder("Desktop")));
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
        folderTree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                    boolean expanded, boolean leaf, int row, boolean hasFocus) {
                JLabel label = (JLabel) super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
                label.setOpaque(selected);
                label.setBackground(selected ? new Color(35, 65, 96) : new Color(37, 37, 37));
                label.setForeground(selected ? new Color(135, 195, 255) : new Color(205, 220, 238));
                label.setBorder(new EmptyBorder(3, 4, 3, 4));
                return label;
            }
        });
        folderTree.addTreeSelectionListener(e -> {
            Object node = folderTree.getLastSelectedPathComponent();
            if (node instanceof DefaultMutableTreeNode dm && dm.getUserObject() instanceof FolderRef ref) {
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

    private void browseDrive(File drive) {
        JFileChooser chooser = new JFileChooser(drive);
        chooser.setDialogTitle("Browse " + drive.getPath() + " — choose files to add as direct links");
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        File[] files = chooser.getSelectedFiles();
        if (files.length == 0 && chooser.getSelectedFile() != null) files = new File[]{chooser.getSelectedFile()};
        int added = 0;
        for (File file : files) {
            try { fs.addPhysicalFile(currentFolderId, file.toPath(), false); added++; }
            catch (Exception ex) { error(ex.getMessage()); }
        }
        refreshAll();
        status(added + " file(s) linked from " + drive.getPath() + ".");
    }

    private void choosePcFolder(String folderName) {
        Path path = Path.of(System.getProperty("user.home"), folderName);
        if (Files.isDirectory(path)) {
            JFileChooser chooser = new JFileChooser(path.toFile());
            chooser.setDialogTitle("Choose a file from " + folderName);
            chooser.setMultiSelectionEnabled(true);
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                File[] files = chooser.getSelectedFiles();
                if (files.length == 0 && chooser.getSelectedFile() != null) files = new File[]{chooser.getSelectedFile()};
                for (File file : files) {
                    try { fs.addPhysicalFile(currentFolderId, file.toPath(), false); }
                    catch (Exception ex) { error(ex.getMessage()); }
                }
                refreshAll();
            }
        } else {
            JOptionPane.showMessageDialog(frame, folderName + " folder was not found. Use Add files from PC to browse manually.");
        }
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
            createButton("Import", false), createButton("Open PC File", false),
            createButton("Rename", false),
            createButton("Move", false), createButton("Delete", false),
            createButton("Undo", false), createButton("Cleanup", false),
            createButton("History", false), createButton("Analyze", false),
            createButton("Save", false), createButton("Load", false)
        };
        buttons[0].addActionListener(e -> importFiles());
        buttons[1].addActionListener(e -> browseAndOpenPcFile());
        buttons[2].addActionListener(e -> renameSelected());
        buttons[3].addActionListener(e -> moveSelected());
        buttons[4].addActionListener(e -> deleteSelected());
        buttons[5].addActionListener(e -> { if (fs.canUndo()) { fs.undo(); refreshAll(); status("Last operation undone."); } else status("Nothing to undo yet."); });
        buttons[6].addActionListener(e -> showCleanup());
        buttons[7].addActionListener(e -> showHistory());
        buttons[8].addActionListener(e -> showAnalytics());
        buttons[9].addActionListener(e -> saveSnapshot());
        buttons[10].addActionListener(e -> loadSnapshot());
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
                try { node = fs.findNode(String.valueOf(idValue)); } catch (RuntimeException ignored) { }
                boolean folder = node instanceof FolderNode;
                label.setText((folder ? "   ▰   " : "   ▧   ") + name);
                label.setFont(new Font("SansSerif", Font.PLAIN, 13));
                label.setForeground(selected ? new Color(125, 190, 255) : (folder ? new Color(105, 175, 255) : new Color(215, 225, 238)));
                label.setBackground(selected ? new Color(38, 65, 92) : new Color(43, 43, 43));
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
                    FsNode selected = selectedNode();
                    if (selected instanceof FolderNode folder) {
                        currentFolderId = folder.id;
                        refreshAll();
                    } else if (selected instanceof FileNode) {
                        previewSelected();
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
        for (FsNode child : folder.sortedChildren.values()) {
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
    }

    private void updatePreviewPanel() {
        int row = fileTable == null ? -1 : fileTable.getSelectedRow();
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

    private void previewSelected() {
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
    }

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
        int added = 0;
        try {
            for (File source : selected) {
                if (source.isFile()) {
                    fs.addPhysicalFile(currentFolderId, source.toPath(), copy);
                    added++;
                }
            }
            refreshAll();
            status(added + " real file(s) added to FileNest.");
            JOptionPane.showMessageDialog(frame, added + " file(s) added successfully. Double-click a file or select it and press Open / Preview.",
                    "Import complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException | RuntimeException ex) {
            refreshAll();
            error("Import stopped: " + ex.getMessage());
        }
    }

    private void browseAndOpenPcFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Browse this PC");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        try {
            FileRecord record = fs.addPhysicalFile(currentFolderId, chooser.getSelectedFile().toPath(), false);
            refreshAll();
            openPhysicalFile(record);
        } catch (IOException | RuntimeException ex) {
            error("Could not add/open this file: " + ex.getMessage());
        }
    }

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
            tableModel.addRow(new Object[]{r.name, r.physicalPath == null ? "File" : (r.managedCopy ? "PC Copy" : "PC Link"), formatBytes(r.size), formatTime(r.lastAccess), r.accessCount, r.id});
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
        long used = fs.totalSize();
        long free = Math.max(0, fs.capacityBytes - used);
        int percent = (int) Math.min(100, Math.round(100.0 * used / fs.capacityBytes));

        List<FileRecord> largest = new ArrayList<>(fs.records.values());
        largest.sort(Comparator.comparingLong((FileRecord r) -> r.size).reversed());
        List<FileRecord> duplicates = fs.findDuplicates();

        StringBuilder report = new StringBuilder("YOUR STORAGE SUMMARY\n\n");
        report.append("Storage used: ").append(formatBytes(used)).append(" of ")
                .append(formatBytes(fs.capacityBytes)).append(" (").append(percent).append("%)\n");
        report.append("Space available: ").append(formatBytes(free)).append("\n");
        report.append("Files: ").append(fs.records.size()).append("     Folders: ")
                .append(Math.max(0, fs.folders.size() - 1)).append("\n\n");

        if (!largest.isEmpty()) {
            FileRecord biggest = largest.get(0);
            report.append("Largest file: ").append(biggest.name).append(" (")
                    .append(formatBytes(biggest.size)).append(")\n");
        } else {
            report.append("Largest file: No files yet\n");
        }

        report.append("Exact duplicate files: ").append(duplicates.size()).append("\n\n");
        report.append("QUICK TIPS\n");
        if (percent >= 80) report.append("• Storage is getting full. Review large files before adding more.\n");
        else report.append("• You have plenty of storage space available.\n");
        if (!duplicates.isEmpty()) report.append("• Check duplicate files to see whether you still need each copy.\n");
        if (largest.size() > 1) report.append("• Preview large files before deciding whether to keep them.\n");
        report.append("• Cleanup Tips only suggests old files; it does not delete anything automatically.");

        JTextArea area = new JTextArea(report.toString(), 18, 48);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setCaretPosition(0);
        area.setFont(new Font("SansSerif", Font.PLAIN, 13));
        JOptionPane.showMessageDialog(frame, new JScrollPane(area), "Quick Storage Analysis",
                JOptionPane.INFORMATION_MESSAGE);
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
        // HashMap provides average O(1) name lookup; TreeMap keeps display/traversal sorted.
        final Map<String, FsNode> children = new HashMap<>();
        final NavigableMap<String, FsNode> sortedChildren = new TreeMap<>(
                Comparator.comparing((String name) -> name.toLowerCase(Locale.ROOT))
                        .thenComparing(Comparator.naturalOrder()));

        FolderNode(String id, String name, FolderNode parent) { super(id, name, parent); }

        void addChild(FsNode node) {
            children.put(node.name, node);
            sortedChildren.put(node.name, node);
        }

        FsNode removeChild(String name) {
            sortedChildren.remove(name);
            return children.remove(name);
        }

        void clearChildren() {
            children.clear();
            sortedChildren.clear();
        }

        void addAllChildren(Map<String, FsNode> nodes) {
            for (FsNode node : nodes.values()) addChild(node);
        }
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
        String physicalPath;
        boolean managedCopy;
        long size, accessCount, version;
        Instant lastAccess;
        FileRecord(String id, String name, String folderId, long size, String content) {
            this.id=id; this.name=name; this.folderId=folderId; this.size=size; this.content=content;
            this.lastAccess=Instant.now(); this.accessCount=0; this.fingerprint=hashContent(content);
        }
        FileRecord(String id, String name, String folderId, long size, String content, String physicalPath, boolean managedCopy, String fingerprint) {
            this(id, name, folderId, size, content);
            this.physicalPath=physicalPath; this.managedCopy=managedCopy;
            if (fingerprint != null) this.fingerprint=fingerprint;
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
            parent.addChild(folder);
            folders.put(folder.id, folder);
            undoStack.push(() -> { parent.removeChild(name); folders.remove(folder.id); });
            return folder;
        }

        FileRecord createFile(String folderId, String name, long size) {
            return createFile(folderId, name, size, "Simulated file content for " + UUID.randomUUID());
        }

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
            }
            Path stored = original;
            if (copy) {
                Path storage = Path.of(System.getProperty("user.home"), "FileNest_Storage");
                Files.createDirectories(storage);
                String fileName = original.getFileName().toString();
                Path destination = storage.resolve(fileName);
                String base = fileName;
                String extension = "";
                int dot = fileName.lastIndexOf('.');
                if (dot > 0) { base = fileName.substring(0, dot); extension = fileName.substring(dot); }
                int suffix = 1;
                while (Files.exists(destination)) destination = storage.resolve(base + "_" + suffix++ + extension);
                Files.copy(original, destination, StandardCopyOption.COPY_ATTRIBUTES);
                stored = destination.toAbsolutePath().normalize();
            }
            String name = stored.getFileName().toString();
            validateName(name);
            if (folder.children.containsKey(name)) throw new IllegalArgumentException("A file or folder named '" + name + "' already exists in this FileNest folder.");
            long size = Files.size(stored);
            if (totalSize() + size > capacityBytes) throw new IllegalArgumentException("The FileNest 10 GB simulated capacity would be exceeded.");
            String fingerprint = hashPhysicalFile(stored);
            String id = UUID.randomUUID().toString();
            FileNode node = new FileNode(id, name, folder);
            folder.addChild(node);
            FileRecord record = new FileRecord(id, name, folderId, size, "", stored.toString(), copy, fingerprint);
            registerFile(record, node);
            undoStack.push(() -> { folder.removeChild(name); unregisterFile(record); detachedRecords.remove(record.id); });
            return record;
        }

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
            folder.addChild(node);
            FileRecord r = new FileRecord(id, name, folderId, size, content);
            registerFile(r, node);
            undoStack.push(() -> { folder.removeChild(name); unregisterFile(r); detachedRecords.remove(r.id); });
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
            parent.removeChild(oldName);
            node.name = newName;
            parent.addChild(node);
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
        }

        // Records are retained only while a delete is undoable; the undo closure restores their indexes.
        private final Map<String, FileRecord> detachedRecords = new HashMap<>();

        private void collectNodes(FsNode node, List<FsNode> out) {
            out.add(node);
            if (node instanceof FolderNode folder)
                for (FsNode child : folder.sortedChildren.values()) collectNodes(child, out);
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
            parent.removeChild(node.name);
            node.name = newName;
            parent.addChild(node);
            if (node instanceof FileNode) {
                FileRecord r = records.get(node.id);
                if (r != null) { nameIndex.remove(r.name, r.id); r.name = newName; nameIndex.insert(newName, r.id); }
            }
        }

        private void moveInternal(FsNode node, FolderNode destination) {
            node.parent.removeChild(node.name);
            destination.addChild(node);
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
            for (FsNode n : folder.sortedChildren.values()) count += n instanceof FileNode ? 1 : countFiles((FolderNode)n);
            return count;
        }
        int countFolders(FolderNode folder) {
            int count = 0;
            for (FsNode n : folder.sortedChildren.values()) if (n instanceof FolderNode f) count += 1 + countFolders(f);
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
                out.writeUTF("FILENEST"); out.writeInt(4); out.writeLong(capacityBytes);
                writeFolder(out, root);
                out.writeInt(lru.size());
                for (String id : lru.keySet()) out.writeUTF(id);
            }
        }
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
        }
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
        private void readFolder(DataInputStream in, FolderNode folder, int version) throws IOException {
            int count = in.readInt();
            readFolderEntries(in, folder, count, version);
        }
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
        }
        private void reset() {
            root.clearChildren(); folders.clear(); folders.put(root.id,root); records.clear();
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
            for (FsNode child : folder.sortedChildren.values()) {
                if (child instanceof FileNode f) total += records.get(f.id).size;
                else if (child instanceof FolderNode f) total += calculateFolderSizes(f, sizes);
            }
            sizes.put(folder.id, total);
            return total;
        }

        long folderSize(FolderNode folder) {
            long total = 0;
            for (FsNode child : folder.sortedChildren.values()) {
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
