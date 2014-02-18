package com.nebula.gradle.plugin.imagebuilder

import javax.annotation.Nonnull
import com.redhat.et.libguestfs.GuestFS;
import com.redhat.et.libguestfs.Partition;
// import com.redhat.et.libguestfs.EventCallback;
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile

// @Category(ImageTask)
class ExportOperations implements Operations {

	static class DiskPartition {
		static final long K = 1024
		static final long M = 1024 * K
		static final long G = 1024 * M
		static final long T = 1024 * G
		String name
		String filesystem = "ext4"
		String mountpoint
		String options = "errors=remount-ro"
		long size = 0
		boolean bootable = false

		// Not to be set by user
		int index
	}

	static class Export {

		@Input
		String outputFileName = 'image'
		@Input
		String outputFormat = 'raw'
		@Input
		List<DiskPartition> partitions = []

		void outputFile(String name, String format = null) {
			outputFileName = name
			if (format)
				outputFormat = format
		}

		void outputFormat(String format) {
			outputFormat = format
		}

		void partition(String name, Closure closure) {
			DiskPartition p = new DiskPartition(name: name);
			p.with closure
			p.index = partitions.size() + 1
			partitions.add(p)
		}

	}

	public void export(Closure c) {
		Export e = new Export()
		e.with c
		customize {
			_export(delegate, e)
		}
	}

	// Attempt to align partitions on 64K boundaries.
	private static long round(long value) {
		long interval = 64 * DiskPartition.K
		return ((value + interval - 1) / interval) * interval
	}

	// Allow an extra 64K for leading and trailing metadata.
	private static long getSize(Export e) {
		long size = 0
		e.partitions.each { size += round(it.size) }
		return size + 64 * DiskPartition.K;
	}

	private static void partition(@Nonnull Export e,
			@Nonnull GuestFS g, @Nonnull String device) {
		boolean gpt = getSize(e) >= 2 * DiskPartition.T
		g.part_init(device, gpt ? "gpt" : "mbr")
		long sectsize = g.blockdev_getss(device)
		println "Sector size of $device is $sectsize"
		long startsect = 64 * DiskPartition.K / sectsize
		e.partitions.each {
			long sectcount = round(it.size) / sectsize
			g.part_add(device, "p", startsect, startsect + sectcount -1)
			if (gpt)
				g.part_set_name(device, it.index, it.name)
			startsect += sectcount
		}
	}

	private static String mount(@Nonnull Export e,
			@Nonnull GuestFS g, @Nonnull String device) {
		boolean gpt = g.part_get_parttype(device) == "gpt"
		String fstab = ""
		Map<String, String> mountpoints = new TreeMap<String, String>();
		e.partitions.each {
			if (it.filesystem == null) {
			} else if (it.filesystem == "swap") {
				g.mkswap_L(it.name ?: "swap", device + it.index)
				if (!gpt)
					g.part_set_mbr_id(device, it.index, 0x82)
				// We use UUIDs because we don't know if the
				// target system or VM will use vda, sda or hda.
				String uuid = g.vfs_uuid(device + it.index)
				fstab += "UUID=$uuid none swap sw 0 0\n" 
			} else {
				String filesystem = it.filesystem ?: "ext4"
				g.mkfs(filesystem, device + it.index)
				String uuid = g.vfs_uuid(device + it.index)
				fstab += "UUID=$uuid ${it.mountpoint} $filesystem ${it.options} 0 0\n" 
				mountpoints.put(it.mountpoint, device + it.index)
			}
			g.part_set_bootable(device, it.index, it.bootable)
		}

		// The TreeMap ensures that these are now in hierarchy order.
		mountpoints.each { String dir, dev ->
			if (dir != "/")
				g.mkdir_p(dir)
			g.mount(dev, dir)
		}

		return fstab
	}

	void _export(ImageTask.Context c, Export e) {
		File imageFile = c as File
		String outputFileName = e.outputFileName + "." + e.outputFormat
		File outputFile = new File(getOutputDir(), outputFileName)

		fsCreate(outputFile, e.outputFormat, getSize(e))
		GuestFS g = fsOpen(outputFile, e.outputFormat) {
			Map<String, Object> optargs = [
				"format"   : imageFormat,
				"readonly" : Boolean.TRUE,
			];
			it.add_drive_opts(imageFile.absolutePath, optargs);
		}

		String targetDevice = fsDevice(g, 2, 0)
		partition(e, g, targetDevice)
		String targetFstab = mount(e, g, targetDevice)

		String sourceDevice = fsDevice(g, 2, 1)
		def sourceMountPath = "/.imagebuilder"
		g.mkdir(sourceMountPath)
		g.mount(sourceDevice, sourceMountPath)
		g.glob_expand(sourceMountPath + "/*").each {
			g.cp_a(it, "/")
		}
		g.umount(sourceMountPath)
		g.rmdir(sourceMountPath)

		// TODO: This should use augeas too.
		g.write('/etc/fstab', targetFstab.getBytes())

/*
		// Booting breaks if we don't do this.
		g.aug_init("/", 0)
		g.aug_set('/files/etc/default/grub/GRUB_TERMINAL', 'console')
		g.aug_set('/files/etc/default/grub/GRUB_CMDLINE_LINUX_DEFAULT', 'nosplash')
		g.aug_save()
		g.aug_close()
*/

		g.mount_vfs("", "proc", "/dev/null", "/proc")
		g.mount_vfs("", "devtmpfs", "/dev/null", "/dev")
		g.mount_vfs("", "sysfs", "/dev/null", "/sys")
		// We need to read /proc/mounts from within the chroot.
		g.command('/bin/cp', '/proc/mounts', '/etc/mtab')
		if (g.exists('/usr/sbin/grub-install'))
			g.command('/usr/sbin/grub-install', '--no-floppy', targetDevice)
		else
			println "No grub-install found; not running."
		if (g.exists('/usr/sbin/update-grub'))
			g.command('/usr/sbin/update-grub')
		else
			println "No grub-mkconfig found; not running."

		fsClose(g)
	}
}
