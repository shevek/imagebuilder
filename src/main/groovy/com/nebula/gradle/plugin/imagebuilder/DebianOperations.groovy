package com.nebula.gradle.plugin.imagebuilder

import javax.annotation.Nonnull
import com.redhat.et.libguestfs.GuestFS;
import com.redhat.et.libguestfs.Partition;
// import com.redhat.et.libguestfs.EventCallback;
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile

// @Category(ImageTask)
class DebianOperations implements Operations {

	static class Debootstrap {
		@Input
		String repository = "http://localhost:3142/ubuntu/"
		@Input
		String release = "quantal"
		@Input
		List<String> components = [ "main" ]
		@Input
		List<String> packages = []

		@Input
		String variant = "-"
		@Input
		List<String> options = []

		@Input
		String installKernel = "/vmlinuz"
		@Input
		String installScript = """#!/bin/sh
/debootstrap/debootstrap --second-stage
/usr/bin/apt-get -y clean
sync
sleep 1
sync
sleep 1
sync
sleep 1
/bin/mount -o remount,ro /
poweroff -f
"""

		void repository(String repository) {
			this.repository = repository
		}

		void release(String release) {
			this.release = release
		}

		void components(String... components) {
			this.components.addAll(components)
		}

		void packages(String... packages) {
			this.packages.addAll(packages)
		}

		void variant(String variant) {
			this.variant = variant
		}

		void options(String... options) {
			this.options.addAll(options)
		}
	}

	void debootstrap(@Nonnull Closure details) {
		Debootstrap d = new Debootstrap()
		d.with details

		customize {
			_debootstrap(delegate, d)
		}
	}

	public void _debootstrap(ImageTask.Context c, Debootstrap d) {
		File tmpDir = getTmpDir();
		File fakerootStateFile = new File(tmpDir, name + '.state')
		def fakerootCommand = [ "fakeroot", "-s", fakerootStateFile ]
		File debootstrapDir = new File(tmpDir, name + '.root')
		debootstrapDir.mkdirs()
		String debootstrapComponents = d.components.join(",")
		String debootstrapPackages = d.packages.join(",")
		def debootstrapCommand = fakerootCommand + [
				"debootstrap",
					"--foreign", "--verbose",
					"--variant=" + d.variant,
					"--keyring", "/etc/apt/trusted.gpg",
					"--components=" + debootstrapComponents,
					"--include=" + debootstrapPackages
			] + d.options + [
					d.release, debootstrapDir, d.repository
			]

		logger.info("Executing " + debootstrapCommand.join(' '))
		project.exec {
			commandLine debootstrapCommand
		}

		File scriptFile = new File(debootstrapDir, 'debootstrap/install');
		scriptFile.text = d.installScript
		ant.chmod(file: scriptFile, perm: 'ugo+rx')

		GuestFS g = c as GuestFS
		// g.mount(fsDevice(g, 1, 0), '/')
		fsUpload(g, debootstrapDir, fakerootStateFile)

		// Requesting the file closes the GuestFS handle.
		File imageFile = c as File
		def qemuCommand = [
			"qemu-system-x86_64",
				"-enable-kvm",
				"-cpu", "host",
				"-m", "512",
				"-hda", imageFile.absolutePath,
				"-kernel", d.installKernel,
				"-append", "rootwait root=/dev/sda rw console=tty0 console=ttyS0 init=/debootstrap/install",
				"-nographic"
		]
		logger.info("Executing " + qemuCommand)
		project.exec {
			commandLine qemuCommand
		}

		project.exec {
			commandLine "e2fsck", "-f", "-y", imageFile
			ignoreExitValue true
		}

		g = c as GuestFS
		g.aug_set('/files/etc/default/grub/GRUB_TERMINAL', 'console')
		g.aug_set('/files/etc/default/grub/GRUB_CMDLINE_LINUX_DEFAULT', 'nosplash')
		g.aug_set('/files/etc/default/locale/LANG', 'en_US.UTF-8')

		// project.delete(debootstrapDir, fakerootStateFile)
	}

	public void kernel_from(final String path) {
		download {
			from path
			into new File(owner.getOutputDir(), 'kernel')
		}
	}

	public void initrd_from(String path) {
		run '/usr/sbin/update-initramfs', '-u'
		download {
			from path
			into new File(owner.getOutputDir(), 'initrd')
		}
	}

}

