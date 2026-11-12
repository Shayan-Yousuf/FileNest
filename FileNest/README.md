# FileNest — Smart File System Simulator

FileNest is a Java Swing desktop application that simulates a hierarchical file system in memory and demonstrates practical Data Structures and Algorithms (DSA). It is designed as a student project with a visual folder explorer, file operations, search, access tracking, cleanup suggestions, duplicate detection, analytics, undo, and save/load.

## Requirements

- JDK 17 or later
- No third-party libraries

## Build and run

From the `FileNest` project directory:

```bash
javac -Xlint:all -d out src/filenest/FileNestApp.java
java -cp out filenest.FileNestApp
```

A prebuilt executable JAR is also included: `java -jar FileNest.jar`.

Windows PowerShell uses the same commands. The application opens a Swing window. Demo folders and files are created on a fresh launch.

## Run regression tests

```bash
javac -Xlint:all -d out src/filenest/FileNestApp.java tests/filenest/FileNestManagerTest.java
java -Djava.awt.headless=true -cp out filenest.FileNestManagerTest
```

The tests use only the JDK and run without opening the GUI. The current regression suite passes 21 checks.

## Implemented features

- **Folder hierarchy:** N-ary tree with expandable Swing folder navigation.
- **Create, rename, move and delete:** Files and folders can be managed. Moving a folder into itself or a descendant is rejected.
- **Recursive folder deletion:** Deleting a folder removes its full subtree from all active indexes. One undo restores the whole subtree.
- **Undo:** Stack-based undo for create, rename, move, file/folder deletion, and file access metadata/LRU changes. Undo is LIFO. Loading a saved snapshot starts a fresh undo history.
- **Prefix search:** Trie-based file-name prefix search.
- **Access tracking and LRU:** Access count and last-access time are tracked; an access-order `LinkedHashMap` maintains least-to-most-recent ordering. A history dialog shows LRU order and recent access events.
- **Cleanup suggestions:** A min-heap identifies old, least-recently-accessed files. Suggestions are advisory; nothing is automatically deleted.
- **Content duplicate detection:** SHA-256 is calculated from simulated file content. The analytics view groups files with matching content hashes. New files allow content to be entered; leaving it blank creates generated demo content.
- **Storage analytics:** Total usage, file/folder counts, largest files, per-folder usage, and duplicate groups.
- **Save/load:** Save the simulated tree and file metadata to a version-3 `.fns` binary snapshot and restore it later.

## DSA mapping

| DSA | FileNest use |
|---|---|
| N-ary tree | Parent/child folder hierarchy |
| HashMap / LinkedHashMap | ID and child indexes; access-order LRU |
| Trie | Prefix search over file names |
| PriorityQueue (min heap) | Oldest-access cleanup candidates |
| Stack (`ArrayDeque`) | LIFO undo actions |
| HashMap + HashSet | SHA-256 duplicate groups |
| Tree traversal | Recursive deletion, folder sizes, and analytics |

See [DSA_COMPLEXITY.md](DSA_COMPLEXITY.md) for operation-by-operation time and space analysis.

## Important scope and limitations

- FileNest is an **in-memory simulation**. It does not read, create, modify, or delete actual files on your computer.
- The entered simulated content is stored in the application model and in `.fns` snapshots. The displayed file size is a separate user-supplied simulated size; it is not calculated from content length.
- Duplicate detection hashes the exact entered simulated content with SHA-256. It does not inspect real files on disk. SHA-256 collisions are theoretically possible, although negligible for this project.
- The save format is a version-3 Java binary format intended for this application, not a general-purpose interchange format. Keep backups of important snapshots.
- Undo history and the recent access-event list are session-only and are not included in saved snapshots. The saved snapshot does preserve file access metadata and the exact LRU ordering. Loading a snapshot clears the current undo stack.
- Cleanup suggestions do not delete files automatically. Review candidates before using Delete.

## Suggested viva explanation

1. **Why a tree?** A folder can contain files and subfolders, so an N-ary tree naturally models the hierarchy.
2. **Why a HashMap?** It provides average O(1) lookup and update for IDs and child names.
3. **Why a Trie?** It navigates a filename prefix character by character instead of scanning every filename.
4. **Why LinkedHashMap for LRU?** Access-order mode maintains recency with average O(1) lookup and repositioning.
5. **Why a min heap?** The smallest timestamp is available at the root, so the oldest access candidate can be inspected efficiently.
6. **Why a stack for undo?** The most recent operation must be reversed first, which is LIFO behavior.
7. **How are duplicates detected?** SHA-256 converts each simulated content string into a fixed-length digest; a hash index groups IDs with the same digest.
8. **Why version numbers in the heap?** Old entries become stale after access changes. Version checks let the cleanup process skip outdated entries safely.
