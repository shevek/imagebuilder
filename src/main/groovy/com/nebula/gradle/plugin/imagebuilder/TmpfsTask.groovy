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
import org.gradle.api.tasks.TaskAction

class TmpfsTask extends AbstractImageTask {

	public TmpfsTask() {
		description = "Mounts a tmpfs to make imagebuilder faster."
		getOutputs().upToDateWhen { false }
	}

	@TaskAction
	@Override
	void run() {
		File tmpDir = getTmpDir()

		tmpDir.mkdirs()
		String stat = qx(['stat', '-f', '-c', '%T', tmpDir.absolutePath])
		if (stat != 'tmpfs') {
			String uid = qx(['id', '-u'])
			String gid = qx(['id', '-g'])
			project.exec {
				commandLine "sudo", "mount",
					"-t", "tmpfs",
					"-o", "size=6G,mode=0755,noatime,uid=$uid,gid=$gid",
					"tmpfs", tmpDir.absolutePath
			}
		}
	}
}
