/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nebula.gradle.plugin.imagebuilder;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.redhat.et.libguestfs.GuestFS;
import com.redhat.et.libguestfs.LibGuestFSException;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nonnull;

/**
 * Temporary utils until ExportOperations is strict-typed.
 *
 * @author shevek
 */
/* pp */ class ExportUtils {

    // Attempt to align partitions on 64K boundaries.
    public static long round(long value) {
        long interval = 64 * ExportDiskPartition.K;
        return ((value + interval - 1) / interval) * interval;
    }

    public static void partition(@Nonnull ExportDisk e,
            @Nonnull GuestFS g, @Nonnull String device)
            throws LibGuestFSException {
        boolean gpt = e.getSize() >= 2 * ExportDiskPartition.T;
        g.part_init(device, gpt ? "gpt" : "mbr");
        long sectsize = g.blockdev_getss(device);
        // println "Sector size of $device is $sectsize";
        long startsect = 64 * ExportDiskPartition.K / sectsize;
        for (ExportDiskPartition partition : e.partitions) {
            long sectcount = round(partition.size) / sectsize;
            g.part_add(device, "p", startsect, startsect + sectcount - 1);
            if (gpt)
                g.part_set_name(device, partition.index, partition.name);
            startsect += sectcount;
        }
    }

    public static String mount(@Nonnull ExportDisk e,
            @Nonnull GuestFS g, @Nonnull String device)
            throws LibGuestFSException {
        boolean gpt = "gpt".equals(g.part_get_parttype(device));
        StringBuilder fstab = new StringBuilder();
        Map<String, String> mountpoints = new TreeMap<String, String>();
        for (ExportDiskPartition partition : e.partitions) {
            if (partition.filesystem == null) {
            } else if ("swap".equals(partition.filesystem)) {
                g.mkswap_L(Objects.firstNonNull(partition.name, "swap"), device + partition.index);
                if (!gpt)
                    g.part_set_mbr_id(device, partition.index, 0x82);
                // We use UUIDs because we don't know if the
                // target system or VM will use vda, sda or hda.
                String uuid = g.vfs_uuid(device + partition.index);
                fstab.append("UUID=" + uuid + " none swap sw 0 0\n");
            } else {
                String filesystem = Objects.firstNonNull(partition.filesystem, "ext4");
                g.mkfs(filesystem, device + partition.index);
                String uuid = g.vfs_uuid(device + partition.index);
                Joiner.on(' ').appendTo(fstab, "UUID=" + uuid, partition.mountpoint, filesystem, partition.options, "0 0\n");
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

        return fstab.toString();
    }

    public static void export(ImageTask.Context c, ExportSystem system) throws IOException, LibGuestFSException {
        final ImageTask task = c.getTask();

        c.close();

        GuestFS g = new GuestFS();
        g.set_trace(true);

        for (ExportDisk disk : system.disks) {
            String outputFileName = disk.outputFileName + "." + disk.outputFormat;
            File outputFile = new File(c.getTask().getOutputDir(), outputFileName);
            task.fsCreate(outputFile, disk.outputFormat, disk.getSize());

            Map<String, Object> optargs = ImmutableMap.<String, Object>of(
                    "format", disk.outputFormat.name(),
                    // TODO: Support readonly for exports.
                    "readonly", Boolean.FALSE
            );
            g.add_drive_opts(outputFile.getAbsolutePath(), optargs);
        }

        Map<String, Object> optargs = ImmutableMap.<String, Object>of(
                "format", task.getImageFormat().toString(),
                "readonly", Boolean.TRUE
        );
        g.add_drive_opts(c.getImageFile().getAbsolutePath(), optargs);

        g.launch();

        StringBuilder fstab = new StringBuilder();
        for (int i = 0; i < system.disks.size(); i++) {
            ExportDisk disk = system.disks.get(i);
            String targetDevice = task.fsDevice(g, i);
            partition(disk, g, targetDevice);
            String fstabFragment = mount(disk, g, targetDevice);
            fstab.append(fstabFragment);
        }

        COPY:
        {
            String sourceDevice = task.fsDevice(g, system.disks.size());
            String sourceMountPath = "/.imagebuilder";
            g.mkdir(sourceMountPath);
            g.mount(sourceDevice, sourceMountPath);
            for (String sourcePath : g.glob_expand(sourceMountPath + "/*")) {
                g.cp_a(sourcePath, "/");
            }
            g.umount(sourceMountPath);
            g.rmdir(sourceMountPath);
        }

        // TODO: This should use augeas too.
        g.write("/etc/fstab", fstab.toString().getBytes());

        /*
         // Booting breaks if we don't do this.
         g.aug_init("/", 0)
         g.aug_set('/files/etc/default/grub/GRUB_TERMINAL', 'console')
         g.aug_set('/files/etc/default/grub/GRUB_CMDLINE_LINUX_DEFAULT', 'nosplash')
         g.aug_save()
         g.aug_close()
         */
        String grubDevice = task.fsDevice(g, 0);
        g.mount_vfs("", "proc", "/dev/null", "/proc");
        g.mount_vfs("", "devtmpfs", "/dev/null", "/dev");
        g.mount_vfs("", "sysfs", "/dev/null", "/sys");
        // We need to read /proc/mounts from within the chroot.
        g.command(new String[]{"/bin/cp", "/proc/mounts", "/etc/mtab"});
        if (g.exists("/usr/sbin/grub-install"))
            g.command(new String[]{"/usr/sbin/grub-install", "--no-floppy", grubDevice});
        else
            task.getLogger().warn("No grub-install found; not running.");
        if (g.exists("/usr/sbin/update-grub"))
            g.command(new String[]{"/usr/sbin/update-grub"});
        else
            task.getLogger().warn("No grub-mkconfig found; not running.");

        task.fsClose(g);
    }
}
