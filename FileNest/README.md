# FileNest — Smart File System Simulator

FileNest is a Java Swing desktop application that combines a simulated file system with optional access to real files on the user's PC. It demonstrates practical Data Structures and Algorithms (DSA) through a folder explorer, file operations, prefix search, access tracking, cleanup suggestions, duplicate detection, analytics, undo, and save/load.

## Requirements

- JDK 17 or later (JDK 21 LTS is recommended)
- Visual Studio Code with Microsoft's **Extension Pack for Java**
- No third-party Java libraries

**Java 8 is not supported.** The source uses Java 17 language/library features. If java -version reports 1.8, select a JDK 17 or 21 before building or running the application. The VS Code source build targets Java 17 bytecode.

## Open and run in Visual Studio Code

1. Extract the project and open **the FileNest folder** in VS Code using **File → Open Folder**. It is the folder containing src, tests, and this README; do not open only FileNestApp.java.
2. Install **Extension Pack for Java** by Microsoft when VS Code recommends it. The recommendation is also saved in .vscode/extensions.json.
3. Press Ctrl+Shift+P, run **Java: Configure Java Runtime**, and select your installed JDK 17 or 21 for this project.
4. Open **Run and Debug** and select **FileNest: Debug GUI**, or open src/filenest/FileNestApp.java and click **Run** above main().

The project includes VS Code tasks. Use **Terminal → Run Task**:

- **FileNest: Build** — compiles the application and regression tests.
- **FileNest: Run GUI** — builds, then starts the Swing application.
- **FileNest: Run Tests** — builds, then runs the headless regression suite.

The launch configuration and tasks are in .vscode/launch.json and .vscode/tasks.json.

### Make JDK 17/21 available to the VS Code terminal on Windows

The Java extension runtime and the Command Prompt PATH can point to different Java installations. The VS Code tasks use javac and java from the terminal's PATH.

1. Find the JDK 21 installation folder (the folder containing bin\java.exe and bin\javac.exe).
2. Set the Windows JAVA_HOME environment variable to that JDK folder, not to its bin subfolder.
3. Add %JAVA_HOME%\bin to your user or system Path and move it above older Java 8 entries.
4. Close all VS Code windows and reopen VS Code.
5. In the VS Code terminal run:

~~~cmd
java -version
javac -version
where java
where javac
~~~

Both version commands should report 17 or newer, and the first paths returned by where should point to the selected JDK.

The supplied run.bat and test.bat scripts prefer %JAVA_HOME% when it is set. The shell scripts use $JAVA_HOME when available.

## Build and run from a terminal

From the FileNest project directory:

~~~bash
javac --release 17 -Xlint:all -d out src/filenest/FileNestApp.java
java -cp out filenest.FileNestApp
~~~

To build and run the regression tests:

~~~bash
javac --release 17 -Xlint:all -d out src/filenest/FileNestApp.java tests/filenest/FileNestManagerTest.java
java -Djava.awt.headless=true -cp out filenest.FileNestManagerTest
~~~

Windows users can also run run.bat or test.bat; macOS/Linux users can run ./run.sh or ./test.sh. The regression tests use only the JDK and do not open the GUI. The current suite contains 25 checks.

A prebuilt executable JAR is included. If it reports UnsupportedClassVersionError, use a JDK new enough for that JAR or compile from source with the commands above.

## Real PC files and simulated files

FileNest supports two different file workflows:

- **Copy into FileNest:** copies selected files into the FileNest_Storage folder under the current user's home directory. FileNest then manages a record pointing to that stored copy.
- **Keep original location (Direct Access):** stores the original absolute path in FileNest without copying the file. If the original is moved or deleted outside FileNest, the saved link may stop working.

Use **Import Files** to select one or more PC files and choose the mode. **Open PC File** registers a selected file as a direct-access entry and opens it. **Open / Preview** opens a real file with the operating system's default application, or shows the in-app preview for a simulated text file. Multi-file import is best-effort per file: one invalid or inaccessible item does not cancel the remaining selections, and the result summary lists added, skipped/already registered, and failed files. Copy mode checks the target folder and capacity before copying, reserves a collision-free name in `FileNest_Storage`, and removes partial copies when an import fails.

FileNest records access when a real file is opened through FileNest. It does **not** monitor unrelated file opens performed elsewhere on the PC or run as a background file-monitoring service.

## Implemented features

- **Folder hierarchy:** N-ary tree with expandable Swing folder navigation.
- **Real file import:** copy files into FileNest_Storage or register their original paths for direct access.
- **Open and preview:** open physical files with the operating system's default application; preview simulated text content in FileNest.
- **Create, rename, move and delete:** manage FileNest entries. Moving a folder into itself or a descendant is rejected.
- **Recursive folder deletion:** removes the selected subtree from active indexes. One undo restores the subtree and its metadata.
- **Undo:** stack-based undo for create, rename, move, file/folder deletion, and file access metadata/LRU changes. Undo is LIFO.
- **Prefix search:** Trie-based file-name prefix search.
- **Access tracking and LRU:** access-order LinkedHashMap maintains least-to-most-recent ordering; the history dialog displays LRU order and recent in-app access events.
- **Cleanup suggestions:** a min-heap identifies old, least-recently-accessed entries. Suggestions are advisory; nothing is automatically deleted.
- **Duplicate detection:** SHA-256 hashes simulated content and streams real file contents from disk to group exact-content matches.
- **Storage analytics:** total usage, file/folder counts, largest files, and duplicate groups.
- **Save/load:** version-4 .fns snapshots preserve the folder tree, file metadata, physical paths, copy/link flags, and LRU order. Version-3 snapshots are also accepted by the loader.

## DSA mapping

| DSA | FileNest use |
|---|---|
| N-ary tree | Parent/child folder hierarchy |
| HashMap / LinkedHashMap | ID and child indexes; access-order LRU |
| Trie | Prefix search over file names |
| PriorityQueue (min heap) | Oldest-access cleanup candidates |
| Stack (ArrayDeque) | LIFO undo actions |
| HashMap + HashSet | SHA-256 duplicate groups |
| Tree traversal | Recursive deletion, folder sizes, and analytics |
| Streaming SHA-256 | Content fingerprinting for real files |

See [DSA_COMPLEXITY.md](DSA_COMPLEXITY.md) for operation-by-operation time and space analysis.

## Important scope and safety limitations

- FileNest is a desktop file organizer, not a background monitor. It records real-file access only when the file is opened through FileNest.
- Rename and Move change the FileNest entry's displayed name/folder. They do not rename or move the underlying physical file on disk.
- Deleting an entry removes it from FileNest's indexes. It does **not** delete the original PC file or the copied file in FileNest_Storage; this avoids accidental data loss. Unreferenced copies may need manual cleanup from that storage folder.
- Direct-access entries depend on the original path continuing to exist and remain readable.
- The displayed storage capacity is a 10 GB FileNest accounting limit. It is not a measurement of the PC's entire disk capacity.
- Simulated file content is stored in the application model and .fns snapshots. Its displayed size is a separate simulated size and is not derived from the content length.
- Physical-file duplicate detection hashes the file bytes with SHA-256 using a fixed 8 KiB read buffer. Simulated-file duplicate detection hashes the entered content string. SHA-256 collisions are theoretically possible, though negligible for this project.
- A .fns snapshot stores physical file paths and copy/link flags, not a second copy of each physical file. Keep the files at their saved paths. Snapshot version 4 is written; version 3 and 4 are accepted when loading.
- Undo history and recent access-event messages are session-only. Loading a snapshot clears the undo stack.
- Cleanup suggestions never delete files automatically. Review candidates before using Delete.

## Suggested viva explanation

1. **Why a tree?** A folder can contain files and subfolders, so an N-ary tree naturally models the hierarchy.
2. **Why a HashMap?** It provides expected O(1) lookup and update for IDs and child names.
3. **Why a Trie?** It navigates a filename prefix character by character instead of scanning every filename.
4. **Why LinkedHashMap for LRU?** Access-order mode maintains recency with expected O(1) lookup and repositioning.
5. **Why a min heap?** The smallest timestamp is at the root, so the oldest access candidate can be inspected efficiently.
6. **Why a stack for undo?** The most recent operation must be reversed first, which is LIFO behavior.
7. **How are duplicates detected?** SHA-256 creates a fixed-length digest from simulated content or streamed physical file bytes; a hash index groups matching digests.
8. **Why version numbers in the heap?** Old entries become stale after access changes. Version checks let cleanup skip outdated entries safely.
9. **What is the direct-access trade-off?** It avoids copying large files, but the entry depends on the original path staying available.
