package com.nebula.gradle.plugin.imagebuilder

import javax.annotation.CheckForNull
import javax.annotation.Nonnull
import org.gradle.api.Project
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.process.ExecResult;
import com.redhat.et.libguestfs.GuestFS;
import com.redhat.et.libguestfs.LibGuestFSException
import com.redhat.et.libguestfs.Partition;

abstract class AbstractImageTask extends ConventionTask {

	@Input
	File tmpDir
	@Input
	File outputDir
	@Input
	String imageFileName = 'image'
	@Input
	String imageFileExtension = '.tmp'
	// @OutputFile
	File imageFile
	@Input
	String imageFormat = 'raw'

	AbstractImageTask() {
		conventionMapping('tmpDir', {
			new File(project.buildDir, 'tmp')
		})
		conventionMapping('outputDir', {
			new File(project.buildDir, 'output')
		})
		conventionMapping('imageFile', {
			String name = getImageFileName() + getImageFileExtension()
			new File(getTmpDir(), name)
		})
	}

	void imageFile(@Nonnull Object file) {
		imageFile = project.file(file);
	}

	void imageFormat(@Nonnull String format) {
		imageFormat = format
	}

	// Must be public, called via owner.qx()
	String qx(@Nonnull List<String> command) {
		ByteArrayOutputStream out = new ByteArrayOutputStream()
		project.exec {
			standardOutput out
			commandLine command
		}
		return out.toString().trim()
	}

	void fsCreate(@Nonnull File imageFile, String imageFormat,
			long size) {
		imageFile.parentFile?.mkdirs()
		project.exec {
			commandLine "qemu-img", "create",
					"-f", imageFormat,
					imageFile.absolutePath, size
		}
	}

	@Nonnull
	GuestFS fsOpen(@Nonnull File imageFile, String imageFormat,
			Closure c = null) {
		GuestFS g = new GuestFS();
		g.set_trace(true);
		Map<String, Object> optargs = [
			"format"   : imageFormat,
			// TODO: Support readonly for exports.
			"readonly" : Boolean.FALSE,
		];
		g.add_drive_opts(imageFile.absolutePath, optargs);
		if (c) {
			g.with c
		}
		g.launch();
		return g
	}

	@Nonnull
	GuestFS fsOpen(Closure c = null) {
		return fsOpen(getImageFile(), getImageFormat(), c)
	}

	@Nonnull
	String fsDevice(@Nonnull GuestFS g,
						int deviceCount,
						int deviceIndex) {
		String[] devices = g.list_devices()
		// println "Devices are " + devices
		return devices[deviceIndex]
	}

	void fsInspect(@Nonnull GuestFS g) {
		Map<String, String> filesystems = g.list_filesystems()
		switch (filesystems.size()) {
			case 0:
				throw new LibGuestFSException("No filesystems found")
			case 1:
				filesystems.each { String device, filesystem ->
					g.mount(device, "/")
				}
				return
		}

		String[] osRoots = g.inspect_os()
		println "Operating system roots are " + osRoots
		osRoots.find { String osRoot ->
			if (g.inspect_get_type(osRoot) != "linux")
				return false
			Map<String, String> mountpoints = g.inspect_get_mountpoints(osRoot)
			println "Operating system mountpoints are " + mountpoints
			// Sort by mount order!
			mountpoints = new TreeMap<String, String>(mountpoints)
			mountpoints.each { String mountpoint, device ->
				g.mount(device, mountpoint)
			}
		}
	}

	private File fsMkfifo() {
		def fifoFile = new File(getTmpDir(), getName() + ".fifo")
		if (fifoFile.exists())
			fifoFile.delete()
		project.exec {
			commandLine "mkfifo", fifoFile.absolutePath
		}
		return fifoFile
	}

	void fsUpload(@Nonnull GuestFS g,
			@Nonnull File srcRoot, @Nonnull File srcState,
			@Nonnull String dstRoot = "/") {
		g.mkdir_p(dstRoot)

		File tarFile = fsMkfifo()

		// Needs to read files only readable as root.
		def fakerootCommand = [ ];
		def tarCommand = fakerootCommand + [
			"tar",
			"-cvf", tarFile.absolutePath,
		]

		if (srcState)
			fakerootCommand = [ "fakeroot", "-s", srcState ]
		else
			tarCommand += [ "--owner", "root", "--group", "root" ]

		tarCommand += [
			"-C", srcRoot.absolutePath,
			"."
		]

		def srcCommand = fakerootCommand + tarCommand
		def tarProcess = srcCommand.execute()
		tarProcess.consumeProcessOutput(System.out, System.err)
		g.tar_in(tarFile.absolutePath, dstRoot)
		tarProcess.waitFor()

		project.delete(tarFile)
	}

	void fsDownload(@Nonnull GuestFS g) {
		assert false : "Not yet implemented."
	}

	void fsClose(@Nonnull GuestFS g) {
		g.sync();
		g.shutdown();
		g.close();
	}
}
