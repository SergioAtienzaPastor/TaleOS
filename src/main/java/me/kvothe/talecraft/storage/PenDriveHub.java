package me.kvothe.talecraft.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

import me.kvothe.talecraft.os.VirtualFileSystem;

/** Global live PenDrive sessions. One id can be claimed by only one reader. */
public final class PenDriveHub {
    public static final long PENDRIVE_QUOTA_BYTES = 1_000_000;

    public interface ComputerAccess {
        VirtualFileSystem fileSystem();

        boolean alive();

        void usbEvent(String event, String side);
    }

    public static final class Drive {
        private final int id;
        private String label;
        private final Path root;
        private final String location;
        private final Map<String, Attachment> attachments = new LinkedHashMap<>();

        private Drive(int id, String label, Path root, String location) {
            this.id = id;
            this.label = label;
            this.root = root;
            this.location = location;
        }

        public int id() {
            return id;
        }

        public String label() {
            return label;
        }

        public Path root() {
            return root;
        }
    }

    private record Attachment(ComputerAccess computer, String side, String mountPath) {
    }

    private final Path dataRoot;
    private final IntSupplier idAllocator;
    private final java.util.function.IntConsumer idReserver;
    private final Map<Integer, Drive> byId = new LinkedHashMap<>();
    private final Map<String, Drive> byLocation = new LinkedHashMap<>();

    public PenDriveHub(Path dataRoot, IntSupplier idAllocator) {
        this(dataRoot, idAllocator, ignored -> { });
    }

    public PenDriveHub(Path dataRoot, IntSupplier idAllocator,
            java.util.function.IntConsumer idReserver) {
        this.dataRoot = dataRoot.toAbsolutePath().normalize();
        this.idAllocator = idAllocator;
        this.idReserver = idReserver;
    }

    public int allocateId() {
        int id = idAllocator.getAsInt();
        if (id < 0) {
            throw new IllegalStateException("negative PenDrive id allocated");
        }
        return id;
    }

    public synchronized Drive insert(String location, Integer requestedId, String requestedLabel)
            throws IOException, VirtualFileSystem.FsException {
        Drive current = byLocation.get(location);
        int id = requestedId == null ? allocateId() : requestedId;
        Drive claimed = byId.get(id);
        if (claimed != null && claimed != current) {
            if (current != null) {
                remove(location);
            }
            throw new IllegalStateException("PenDrive #" + id + " is already inserted at "
                    + claimed.location);
        }
        if (current != null && current.id == id) {
            current.label = PenDriveItemMetadata.normalizeLabel(requestedLabel, id);
            return current;
        }
        if (requestedId != null) {
            idReserver.accept(id);
        }
        if (current != null) {
            remove(location);
        }
        String label = PenDriveItemMetadata.normalizeLabel(requestedLabel, id);
        Drive drive = new Drive(id, label, dataRoot.resolve(String.valueOf(id)), location);
        java.nio.file.Files.createDirectories(drive.root);
        byId.put(id, drive);
        byLocation.put(location, drive);
        return drive;
    }

    public synchronized Drive remove(String location) {
        Drive drive = byLocation.remove(location);
        if (drive == null) {
            return null;
        }
        byId.remove(drive.id, drive);
        for (String computerKey : new ArrayList<>(drive.attachments.keySet())) {
            detach(drive, computerKey);
        }
        return drive;
    }

    public synchronized Drive atLocation(String location) {
        return byLocation.get(location);
    }

    public synchronized Drive byId(int id) {
        return byId.get(id);
    }

    public synchronized int activeDriveCount() {
        return byId.size();
    }

    public synchronized void attachComputer(String location, String computerKey,
            ComputerAccess computer, String side) throws IOException, VirtualFileSystem.FsException {
        attachComputer(location, computerKey, computer, side, null);
    }

    public synchronized void attachComputer(String location, String computerKey,
            ComputerAccess computer, String side, String requestedMountPath)
            throws IOException, VirtualFileSystem.FsException {
        Drive drive = byLocation.get(location);
        if (drive == null || computer == null || !computer.alive()) {
            return;
        }
        Attachment previous = drive.attachments.get(computerKey);
        if (previous != null) {
            if (computer.fileSystem().isMounted(previous.mountPath())) {
                return;
            }
            drive.attachments.remove(computerKey);
        }
        VirtualFileSystem fs = computer.fileSystem();
        String mountPath = requestedMountPath;
        if (mountPath == null) {
            for (int index = 1; mountPath == null; index++) {
                String candidate = index == 1 ? "usb" : "usb" + index;
                if (!fs.isMounted(candidate)) {
                    mountPath = candidate;
                }
            }
        } else if (fs.isMounted(mountPath)) {
            throw new VirtualFileSystem.FsException("mount is already attached: " + mountPath);
        }
        fs.mount(mountPath, drive.root, PENDRIVE_QUOTA_BYTES);
        drive.attachments.put(computerKey, new Attachment(computer, side, mountPath));
        computer.usbEvent("usb", side);
    }

    public synchronized void detachComputer(String computerKey) {
        for (Drive drive : byId.values()) {
            if (drive.attachments.containsKey(computerKey)) {
                detach(drive, computerKey);
            }
        }
    }

    public synchronized void detachComputer(String location, String computerKey) {
        Drive drive = byLocation.get(location);
        if (drive != null) {
            detach(drive, computerKey);
        }
    }

    public synchronized String mountPath(String location, String computerKey) {
        Drive drive = byLocation.get(location);
        Attachment attachment = drive == null ? null : drive.attachments.get(computerKey);
        return attachment == null ? null : attachment.mountPath;
    }

    public synchronized boolean attachmentActive(String location, String computerKey,
            String expectedMountPath) {
        Drive drive = byLocation.get(location);
        Attachment attachment = drive == null ? null : drive.attachments.get(computerKey);
        if (attachment == null || !attachment.mountPath().equals(expectedMountPath)) {
            return false;
        }
        try {
            return attachment.computer().fileSystem().isMounted(expectedMountPath);
        } catch (VirtualFileSystem.FsException exception) {
            return false;
        }
    }

    public synchronized void setLabel(String location, String label) {
        Drive drive = requireDrive(location);
        drive.label = PenDriveItemMetadata.normalizeLabel(label, drive.id);
    }

    public synchronized List<String> locations() {
        return List.copyOf(byLocation.keySet());
    }

    public synchronized void clear() {
        for (String location : new ArrayList<>(byLocation.keySet())) {
            remove(location);
        }
    }

    private Drive requireDrive(String location) {
        Drive drive = byLocation.get(location);
        if (drive == null) {
            throw new IllegalStateException("No PenDrive present");
        }
        return drive;
    }

    private void detach(Drive drive, String computerKey) {
        Attachment attachment = drive.attachments.remove(computerKey);
        if (attachment == null) {
            return;
        }
        try {
            attachment.computer.fileSystem().unmount(attachment.mountPath);
        } catch (VirtualFileSystem.FsException ignored) {
        }
        if (attachment.computer.alive()) {
            attachment.computer.usbEvent("usb_eject", attachment.side);
        }
    }
}
