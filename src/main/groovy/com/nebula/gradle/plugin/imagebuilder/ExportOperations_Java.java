package com.nebula.gradle.plugin.imagebuilder;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.redhat.et.libguestfs.GuestFS;
import com.redhat.et.libguestfs.LibGuestFSException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nonnull;
import org.anarres.qemu.image.QEmuImageFormat;
import org.gradle.api.Action;
import org.gradle.api.tasks.Input;

// @Category(ImageTask)
public class ExportOperations_Java implements Operations {

    public static class DiskPartition {

        public static final long K = 1024;
        public static final long M = 1024 * K;
        public static final long G = 1024 * M;
        public static final long T = 1024 * G;
        public String name;
        public String filesystem = "ext4";
        public String mountpoint;
        public String options = "errors=remount-ro";
		public long size = 0;
        public boolean bootable = false;

        public DiskPartition(String name) {
            this.name = name;
        }

        // Not to be set by user
        private int index;
    }

    public static class Export {

        @Input
        String outputFileName = "image";
        @Input
        QEmuImageFormat outputFormat = QEmuImageFormat.raw;
        @Input
        List<DiskPartition> partitions = new ArrayList<DiskPartition>();

        public void outputFile(String name) {
            outputFileName = name;
        }

        public void outputFile(String name, String format) {
            outputFile(name);
            if (format != null)
                outputFormat(format);
        }

        void outputFormat(QEmuImageFormat format) {
            outputFormat = format;
        }

        void outputFormat(String format) {
            outputFormat(QEmuImageFormat.valueOf(format));
        }

        void partition(String name, Action<DiskPartition> closure) {
            DiskPartition p = new DiskPartition(name);
            closure.execute(p);
            p.index = partitions.size() + 1;
            partitions.add(p);
        }

    }

    private final ImageTask task;

    public ExportOperations_Java(ImageTask task) {
        this.task = task;
    }

    public void export(Action<Export> c) {
        final Export e = new Export();
        c.execute(e);
        task.customize(new Action<ImageTask.Context>() {
            @Override
            public void execute(ImageTask.Context t) {
                try {
                    _export(t, e);
                } catch (IOException ex) {
                    throw Throwables.propagate(ex);
                } catch (LibGuestFSException ex) {
                    throw Throwables.propagate(ex);
                }
            }
        });
    }

    // Attempt to align partitions on 64K boundaries.
    private static long round(long value) {
        long interval = 64 * DiskPartition.K;
        return ((value + interval - 1) / interval) * interval;
    }

    // Allow an extra 64K for leading and trailing metadata.
    private static long getSize(Export e) {
        long size = 0;
        for (DiskPartition partition : e.partitions)
            size += round(partition.size);
        return size + 64 * DiskPartition.K;
    }

    private static void partition(@Nonnull Export e,
            @Nonnull GuestFS g, @Nonnull String device) throws LibGuestFSException {
        boolean gpt = getSize(e) >= 2 * DiskPartition.T;
        g.part_init(device, gpt ? "gpt" : "mbr");
        long sectsize = g.blockdev_getss(device);
        // println "Sector size of $device is $sectsize";
        long startsect = 64 * DiskPartition.K / sectsize;
        for (DiskPartition partition : e.partitions) {
            long sectcount = round(partition.size) / sectsize;
            g.part_add(device, "p", startsect, startsect + sectcount - 1);
            if (gpt)
                g.part_set_name(device, partition.index, partition.name);
            startsect += sectcount;
        }
    }

    private static String mount(@Nonnull Export e,
            @Nonnull GuestFS g, @Nonnull String device) throws LibGuestFSException {
        boolean gpt = "gpt".equals(g.part_get_parttype(device));
        String fstab = "";
        Map<String, String> mountpoints = new TreeMap<String, String>();
        for (DiskPartition partition : e.partitions) {
            if (partition.filesystem == null) {
            } else if ("swap".equals(partition.filesystem)) {
                g.mkswap_L(Objects.firstNonNull(partition.name, "swap"), device + partition.index);
                if (!gpt)
                    g.part_set_mbr_id(device, partition.index, 0x82);
                // We use UUIDs because we don't know if the
                // target system or VM will use vda, sda or hda.
                String uuid = g.vfs_uuid(device + partition.index);
                fstab += "UUID=" + uuid + " none swap sw 0 0\n";
            } else {
                String filesystem = Objects.firstNonNull(partition.filesystem, "ext4");
                g.mkfs(filesystem, device + partition.index);
                String uuid = g.vfs_uuid(device + partition.index);
                fstab += "UUID=" + uuid + " ${it.mountpoint} $filesystem ${it.options} 0 0\n";    // TODO!
                mountpoints.put(partition.mountpoint, device + partition.index);
            }
            g.part_set_bootable(device, partition.index, partition.bootable);
        }

        // The TreeMap ensures that these are now in hierarchy order.
        for (Map.Entry<String, String> mountpoint : mountpoints.entrySet()) {
            String dir = mountpoint.getKey();
            String dev = mountpoint.getValue();
            if (!"/".equals(dir))
                g.mkdir_p(dir);
            g.mount(dev, dir);
        }

        return fstab;
    }

    void _export(ImageTask.Context c, Export e) throws IOException, LibGuestFSException {
        final ImageTask task = c.getTask();
        final File imageFile = c.getImageFile();

        String outputFileName = e.outputFileName + "." + e.outputFormat;
        File outputFile = new File(c.getTask().getOutputDir(), outputFileName);

        task.fsCreate(outputFile, e.outputFormat, getSize(e));
        GuestFS g = task.fsOpen(outputFile, e.outputFormat, new Action<GuestFS>() {
            @Override
            public void execute(GuestFS g) {
                try {
                    Map<String, Object> optargs = ImmutableMap.<String, Object>of(
                            "format", task.getImageFormat().toString(),
                            "readonly", Boolean.TRUE
                    );
                    g.add_drive_opts(imageFile.getAbsolutePath(), optargs);
                } catch (Exception ex) {
                    throw Throwables.propagate(ex);
                }
            }
        });

        String targetDevice = task.fsDevice(g, 2, 0);
        partition(e, g, targetDevice);
        String targetFstab = mount(e, g, targetDevice);

        String sourceDevice = task.fsDevice(g, 2, 1);
        String sourceMountPath = "/.imagebuilder";
        g.mkdir(sourceMountPath);
        g.mount(sourceDevice, sourceMountPath);
        for (String sourcePath : g.glob_expand(sourceMountPath + "/*")) {
            g.cp_a(sourcePath, "/");
        }
        g.umount(sourceMountPath);
        g.rmdir(sourceMountPath);

        // TODO: This should use augeas too.
        g.write("/etc/fstab", targetFstab.getBytes());

        /*
         // Booting breaks if we don't do this.
         g.aug_init("/", 0)
         g.aug_set('/files/etc/default/grub/GRUB_TERMINAL', 'console')
         g.aug_set('/files/etc/default/grub/GRUB_CMDLINE_LINUX_DEFAULT', 'nosplash')
         g.aug_save()
         g.aug_close()
         */
        g.mount_vfs("", "proc", "/dev/null", "/proc");
        g.mount_vfs("", "devtmpfs", "/dev/null", "/dev");
        g.mount_vfs("", "sysfs", "/dev/null", "/sys");
        // We need to read /proc/mounts from within the chroot.
        g.command(new String[]{"/bin/cp", "/proc/mounts", "/etc/mtab"});
        if (g.exists("/usr/sbin/grub-install"))
            g.command(new String[]{"/usr/sbin/grub-install", "--no-floppy", targetDevice});
        else
            task.getLogger().warn("No grub-install found; not running.");
        if (g.exists("/usr/sbin/update-grub"))
            g.command(new String[]{"/usr/sbin/update-grub"});
        else
            task.getLogger().warn("No grub-mkconfig found; not running.");

        task.fsClose(g);
    }
}
