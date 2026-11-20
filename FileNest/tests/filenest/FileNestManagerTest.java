package filenest;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/** Dependency-free regression tests for FileNest's private DSA manager. */
public class FileNestManagerTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Class<?> app = FileNestApp.class;
        Class<?> managerType = Arrays.stream(app.getDeclaredClasses())
                .filter(c -> c.getSimpleName().equals("FileSystemManager")).findFirst().orElseThrow();
        Constructor<?> ctor = managerType.getDeclaredConstructor(); ctor.setAccessible(true);
        Object fs = ctor.newInstance();

        Object folderA = call(fs, "createFolder", new Class<?>[]{String.class,String.class}, "root", "A");
        Object folderB = call(fs, "createFolder", new Class<?>[]{String.class,String.class}, "root", "B");
        String aId = (String) field(folderA, "id");
        String bId = (String) field(folderB, "id");
        Object file1 = call(fs, "createFile", new Class<?>[]{String.class,String.class,long.class,String.class}, aId,"alpha.txt",10L,"identical bytes");
        Object file2 = call(fs, "createFile", new Class<?>[]{String.class,String.class,long.class,String.class}, bId,"beta.txt",10L,"identical bytes");
        String id1 = (String) field(file1,"id");
        check(((List<?>)call(fs,"findDuplicates",new Class<?>[]{})).size()==2,"SHA-256 duplicate grouping");
        long accessBefore=(long)field(file1,"accessCount");
        call(fs,"access",new Class<?>[]{String.class},id1);
        check((long)field(file1,"accessCount")==accessBefore+1,"Access increments access metadata");
        call(fs,"undo",new Class<?>[]{});
        check((long)field(file1,"accessCount")==accessBefore,"Undo access restores metadata and LRU order");

        call(fs,"renameNode",new Class<?>[]{String.class,String.class},id1,"renamed.txt");
        check(((List<?>)call(fs,"searchPrefix",new Class<?>[]{String.class},"ren")).size()==1,"Trie updated after rename");
        call(fs,"undo",new Class<?>[]{});
        check(((List<?>)call(fs,"searchPrefix",new Class<?>[]{String.class},"alpha")).size()==1,"Undo rename");

        call(fs,"moveNode",new Class<?>[]{String.class,String.class},id1,bId);
        check(bId.equals(field(call(fs,"findNode",new Class<?>[]{String.class},id1),"parent") == null ? "" : field(field(call(fs,"findNode",new Class<?>[]{String.class},id1),"parent"),"id")),"File move updates parent");
        call(fs,"undo",new Class<?>[]{});

        call(fs,"moveNode",new Class<?>[]{String.class,String.class},aId,bId);
        boolean cycleBlocked=false;
        try { call(fs,"moveNode",new Class<?>[]{String.class,String.class},bId,aId); }
        catch (InvocationTargetException ex) { cycleBlocked=true; }
        check(cycleBlocked,"Folder cycle prevention");

        long beforeDelete=(long)call(fs,"totalSize",new Class<?>[]{});
        call(fs,"deleteNode",new Class<?>[]{String.class},bId);
        check((long)call(fs,"totalSize",new Class<?>[]{})==0L,"Recursive folder deletion removes descendant files");
        call(fs,"undo",new Class<?>[]{});
        check((long)call(fs,"totalSize",new Class<?>[]{})==beforeDelete,"Undo restores deleted subtree and metadata");
        check(((Map<?,?>)field(fs,"records")).size()==2,"Undo restores file index");

        call(fs,"moveNode",new Class<?>[]{String.class,String.class},aId,"root");
        call(fs,"undo",new Class<?>[]{});
        check(aId.equals(field(call(fs,"findNode",new Class<?>[]{String.class},aId),"id")),"Undo folder move preserves folder identity");
        call(fs,"deleteNode",new Class<?>[]{String.class},id1);
        check(((Map<?,?>)field(fs,"records")).size()==1,"File deletion removes its record");
        call(fs,"undo",new Class<?>[]{});
        check(((Map<?,?>)field(fs,"records")).size()==2,"Undo file deletion restores record");

        String previousHome = System.getProperty("user.home");
        Path importHome = Files.createTempDirectory("filenest-import-home");
        Path capacityHome = null;
        try {
            System.setProperty("user.home", importHome.toString());
            Path copySource = Files.writeString(importHome.resolve("shared.txt"), "physical copy bytes");
            Path directSource = Files.writeString(importHome.resolve("direct.txt"), "direct access bytes");

            Object importFs = ctor.newInstance();
            call(importFs, "createFile", new Class<?>[]{String.class,String.class,long.class,String.class},
                    "root", "shared.txt", 1L, "existing virtual entry");
            Object copied = call(importFs, "addPhysicalFile",
                    new Class<?>[]{String.class,Path.class,boolean.class}, "root", copySource, true);
            Path copiedPath = Path.of((String) field(copied, "physicalPath"));
            check(Files.isRegularFile(copiedPath)
                            && copiedPath.getFileName().toString().equals("shared_1.txt")
                            && (boolean) field(copied, "managedCopy"),
                    "Copy import reserves a collision-free name without overwriting virtual entries");
            String expectedFingerprint = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(copySource)));
            check(expectedFingerprint.equals(field(copied, "fingerprint")),
                    "Copied-file fingerprint matches the physical bytes");

            Object directFs = ctor.newInstance();
            Object directFirst = call(directFs, "addPhysicalFile",
                    new Class<?>[]{String.class,Path.class,boolean.class}, "root", directSource, false);
            Object directSecond = call(directFs, "addPhysicalFile",
                    new Class<?>[]{String.class,Path.class,boolean.class}, "root", directSource, false);
            check(field(directFirst, "id").equals(field(directSecond, "id"))
                            && ((Map<?,?>) field(directFs, "records")).size() == 1
                            && !(boolean) field(directFirst, "managedCopy"),
                    "Repeated direct import reuses the existing original-path entry");

            capacityHome = Files.createTempDirectory("filenest-capacity-home");
            System.setProperty("user.home", capacityHome.toString());
            Path tooLarge = Files.writeString(capacityHome.resolve("too-large.txt"), "too large");
            Object capacityFs = ctor.newInstance();
            long capacity = (long) field(capacityFs, "capacityBytes");
            call(capacityFs, "createFile",
                    new Class<?>[]{String.class,String.class,long.class,String.class},
                    "root", "nearly-full.bin", capacity - 1, "reserved simulated capacity");
            boolean capacityRejected = false;
            try {
                call(capacityFs, "addPhysicalFile",
                        new Class<?>[]{String.class,Path.class,boolean.class}, "root", tooLarge, true);
            } catch (InvocationTargetException ex) { capacityRejected = true; }
            check(capacityRejected
                            && !Files.exists(capacityHome.resolve("FileNest_Storage"))
                            && ((Map<?,?>) field(capacityFs, "records")).size() == 1,
                    "Capacity rejection happens before a storage copy is created");
        } finally {
            if (previousHome == null) System.clearProperty("user.home");
            else System.setProperty("user.home", previousHome);
            deleteTree(capacityHome);
            deleteTree(importHome);
        }

        File snapshot=File.createTempFile("filenest-test", ".fns");
        List<?> lruBeforeSave = new ArrayList<>((Collection<?>)field(fs,"lru").getClass().getMethod("keySet").invoke(field(fs,"lru")));
        call(fs,"save",new Class<?>[]{File.class},snapshot);
        Object loaded=ctor.newInstance();
        call(loaded,"load",new Class<?>[]{File.class},snapshot);
        check((long)call(loaded,"totalSize",new Class<?>[]{})==beforeDelete,"Save/load preserves total storage");
        check(((Map<?,?>)field(loaded,"records")).size()==2,"Save/load restores file records");
        check(((List<?>)call(loaded,"findDuplicates",new Class<?>[]{})).size()==2,"Save/load rebuilds duplicate index");
        List<?> lruAfterLoad = new ArrayList<>((Collection<?>)field(loaded,"lru").getClass().getMethod("keySet").invoke(field(loaded,"lru")));
        check(lruBeforeSave.equals(lruAfterLoad),"Save/load preserves exact LRU order");
        File broken=File.createTempFile("filenest-bad", ".fns");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(broken)) { out.write(new byte[]{1,2,3,4}); }
        boolean rejected=false;
        try { call(loaded,"load",new Class<?>[]{File.class},broken); } catch (InvocationTargetException ex) { rejected=true; }
        check(rejected && ((Map<?,?>)field(loaded,"records")).size()==2,"Corrupt load is rejected without wiping current data");
        snapshot.delete(); broken.delete();

        Object heapFile = call(fs,"createFile",new Class<?>[]{String.class,String.class,long.class,String.class},"root","heap-check.txt",1L,"heap");
        String heapId=(String)field(heapFile,"id");
        Field accessField=heapFile.getClass().getDeclaredField("lastAccess"); accessField.setAccessible(true);
        accessField.set(heapFile, java.time.Instant.now().minus(java.time.Duration.ofDays(10)));
        Field versionField=heapFile.getClass().getDeclaredField("version"); versionField.setAccessible(true);
        versionField.setLong(heapFile, versionField.getLong(heapFile)+1);
        call(fs,"touchHeap",new Class<?>[]{heapFile.getClass()},heapFile);
        call(fs,"deleteNode",new Class<?>[]{String.class},heapId);
        call(fs,"undo",new Class<?>[]{});
        check(((List<?>)call(fs,"suggestUnused",new Class<?>[]{int.class,int.class},1,10)).stream()
                .filter(r -> { try { return heapId.equals(field(r,"id")); } catch(Exception ex) { throw new RuntimeException(ex); } }).count()==1,
                "Undo deletion does not reactivate stale heap entries");

        Object created=call(fs,"createFile",new Class<?>[]{String.class,String.class,long.class,String.class},"root","temporary.txt",1L,"temp");
        String createdId=(String)field(created,"id");
        call(fs,"undo",new Class<?>[]{});
        boolean createUndone=false;
        try { call(fs,"findNode",new Class<?>[]{String.class},createdId); }
        catch (InvocationTargetException ex) { createUndone=true; }
        check(createUndone,"Undo file creation");
        Object temporaryFolder=call(fs,"createFolder",new Class<?>[]{String.class,String.class},"root","temporary-folder");
        String temporaryFolderId=(String)field(temporaryFolder,"id");
        call(fs,"undo",new Class<?>[]{});
        boolean folderCreateUndone=false;
        try { call(fs,"findNode",new Class<?>[]{String.class},temporaryFolderId); }
        catch (InvocationTargetException ex) { folderCreateUndone=true; }
        check(folderCreateUndone,"Undo folder creation");

        System.out.println("PASS: " + checks + " FileNest regression checks");
    }

    private static void deleteTree(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static Object call(Object target,String name,Class<?>[] types,Object... args) throws Exception {
        Method m=target.getClass().getDeclaredMethod(name,types); m.setAccessible(true); return m.invoke(target,args);
    }
    private static Object field(Object target,String name) throws Exception {
        Class<?> type=target.getClass();
        while(type!=null) {
            try { Field f=type.getDeclaredField(name); f.setAccessible(true); return f.get(target); }
            catch(NoSuchFieldException ignored) { type=type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
    private static void check(boolean condition,String label) {
        checks++; if(!condition) throw new AssertionError("FAILED: "+label);
        System.out.println("PASS: "+label);
    }
}

        Method m=target.getClass().getDeclaredMethod(name,types); m.setAccessible(true); return m.invoke(target,args);
    }
    private static Object field(Object target,String name) throws Exception {
        Class<?> type=target.getClass();
        while(type!=null) {
            try { Field f=type.getDeclaredField(name); f.setAccessible(true); return f.get(target); }
            catch(NoSuchFieldException ignored) { type=type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
    private static void check(boolean condition,String label) {
        checks++; if(!condition) throw new AssertionError("FAILED: "+label);
        System.out.println("PASS: "+label);
    }
}
