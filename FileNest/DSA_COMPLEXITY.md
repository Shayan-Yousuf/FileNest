# FileNest — DSA and Complexity Notes

Let:
- `F` = number of files in the relevant structure
- `D` = number of folders in the relevant structure
- `L` = filename length
- `H` = folder-tree height
- `C` = number of content bytes hashed
- `K` = number of matching search results
- `V` = number of Trie nodes visited below a matched prefix
- `N` = number of valid files represented in the heap/LRU

Hash table operations below are expected/average-case O(1), not guaranteed worst-case O(1).

## Data structures

- **N-ary tree:** A folder owns a map of child names to file/folder nodes. This represents the hierarchy.
- **HashMaps:** Folder IDs, file records, and duplicate digest groups are indexed for average O(1) lookup/update.
- **Trie:** File names are indexed character by character for prefix search.
- **LinkedHashMap:** Access-order mode maintains least-recently-used to most-recently-used order.
- **PriorityQueue:** A binary min heap orders timestamp entries for cleanup suggestions. Version numbers identify stale entries.
- **ArrayDeque stack:** Stores inverse operations for LIFO undo.
- **SHA-256 + HashMap/HashSet:** Simulated content is hashed and IDs with the same digest are grouped.

## Operation complexity

| Operation | Expected time | Extra space / notes |
|---|---:|---|
| Folder/file lookup by ID | O(1) average | O(1) |
| Child lookup by name | O(1) average | O(1) |
| Create folder | O(1) average | O(1) |
| Create file | O(F + D + C + L + log N) | O(C) for simulated content; capacity check traverses the tree, Trie insert is O(L), heap insert is O(log N) |
| Rename folder | O(1) average | O(1) |
| Rename file | O(L) | Trie path update; O(L) possible new Trie nodes |
| Move file | O(1) average | O(1) |
| Move folder | O(H) | O(1); ancestor walk prevents cycles |
| Access file with undo | O(N + log N) | O(N) snapshot preserves the exact prior LRU order; the underlying LRU touch itself is O(1) average |
| Trie insert/remove | O(L) | O(L) worst-case new nodes on insert |
| Trie prefix search | O(L + V + K log K) | O(K) result set/list; sorting adds K log K |
| Heap insert | O(log N) | O(1) auxiliary |
| Heap remove-min | O(log N) | O(1) auxiliary |
| Heap peek | O(1) | O(1) |
| Cleanup suggestions | O(P log M) worst-case for P heap entries examined | O(R) returned candidates plus deferred entries; stale entries may be skipped |
| Recursive folder size/count | O(F + D) | O(H) recursion stack |
| Recursive folder deletion | O(N + Fs·L + Ds) | O(N + Fs + Ds): exact prior LRU order plus subtree snapshot; `Fs`,`Ds` are subtree file/folder counts |
| Undo create/rename/move | O(L) for file create/rename; O(1) average for folder create/rename and moves | Trie removal/reinsert for file names is O(L) |
| Undo access | O(N + log N) | O(N) LRU-order snapshot |
| Undo recursive delete | O(N + Fs·L + Fs log N + Ds) | Restores subtree indexes and exact prior LRU order |
| Duplicate grouping lookup | O(1) average per digest lookup | Digest storage O(F); hashing all contents initially costs O(total content bytes) |
| List all duplicates | O(F log F) | O(F) result storage; results are sorted by digest |
| Storage analytics | O(F log F + D) | O(F + D) for one-pass folder totals, largest-file sorting, and output |
| Save snapshot | O(F + D + total content bytes) | O(F + D + total content bytes) serialized data; LRU ordering is preserved |
| Load snapshot | O(F log F + sum of filename lengths + total content bytes + D) | O(F + D + total content bytes) to parse and rebuild indexes |

`P` is the number of heap entries polled during cleanup and `M` is the heap size at that point. Heap entries can be stale because access creates a newer timestamp/version entry; stale entries are discarded when polled. The current implementation does not compact the heap periodically, so repeated accesses can increase heap memory until stale entries are consumed or the app is restarted/loaded. The undo feature also snapshots the full LRU ordering on each access; this makes access undo exact but raises the full operation to O(N), even though the underlying LinkedHashMap touch is O(1) average.

## Complexity trade-offs and accuracy

- Trie prefix search first walks `L` characters, then visits the matching subtree. Collecting `K` IDs is linear in returned IDs; sorting those results costs O(K log K).
- Folder moves validate cycles by walking destination ancestors, O(H), rather than traversing the moved subtree.
- Recursive deletion visits each subtree node and updates file indexes. File Trie removals add the sum of filename lengths; restoring files also adds heap insertions.
- Undo for access stores the prior LRU ordering, so that specific undo is O(N) in time and space. This is an intentional correctness trade-off for restoring the exact prior recency order.
- Duplicate detection is content-based for the simulator's stored content string, not based on file name or simulated size. SHA-256 hashing is O(C) for C bytes.
- The project prioritizes clarity and demonstrable DSA over production-scale filesystem performance. A production application would add heap compaction, transactional persistence, streaming content hashes, and stronger corruption recovery.
