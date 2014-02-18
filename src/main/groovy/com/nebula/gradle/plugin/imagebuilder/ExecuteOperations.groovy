package com.nebula.gradle.plugin.imagebuilder

import com.redhat.et.libguestfs.GuestFS

// @Category(ImageTask)
class ExecuteOperations implements Operations {

	void run(String... commandLine) {
		customize {
			GuestFS g = delegate as GuestFS
			g.command(commandLine)
		}
	}
}
