package com.nebula.gradle.plugin.imagebuilder

import javax.annotation.CheckForNull
import javax.annotation.Nonnull
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory

@Deprecated // Just leftovers from the Makefile.
class Debian {

	@Input
	boolean bootstrap = true
	@Input
	boolean deactivate = false
	@Input
	boolean reactivate = false

	static void divert(File root, String name) {
		sudo(root, "dpkg-divert --local --divert ${name}.orig --rename ${name}")
		link(root, name, "/bin/true")
	}

	static void undivert(File root, String name) {
		rm(root, name)
		sudo(root, "dpkg-divert --rename --remove $name")
	}

	static void deactivate(File root) {
		divert(root, "/sbin/initctl")
		divert(root, "/usr/sbin/update-initramfs")

		def policyText = """
echo "************************************" >&2
echo "All rc.d operations denied by policy" >&2
echo "************************************" >&2
exit 101
"""

		// # Copy in policy file.
		// $(CP) -p ./src/resources/policy-rc.d $(ROOT)/usr/sbin/policy-rc.d
		// chmod 755 $(ROOT)/usr/sbin/policy-rc.d
	}

	static void activate(File root) {
		undivert(root, "/usr/sbin/update-initramfs")
		sudo(root, "update-initramfs -u")
		undivert(root, "/sbin/initctl")
		rm(root, "/usr/sbin/policy-rc.d")
	}

	static void cleanup(File root) {
		sudo(root, "apt-get clean")
		sudo(root, "dpkg --configure --pending")
	}
}
